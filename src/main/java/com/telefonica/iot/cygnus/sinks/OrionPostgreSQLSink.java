/**
 * Copyright 2015 Telefonica Investigación y Desarrollo Chile
 *
 * This file is part of fiware-cygnus (FI-WARE project).
 *
 * fiware-cygnus is free software: you can redistribute it and/or modify it under the terms of the GNU Affero
 * General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * fiware-cygnus is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with fiware-cygnus. If not, see
 * http://www.gnu.org/licenses/.
 *
 * For those usages not covered by the GNU Affero General Public License please contact with iot_support at tid dot es
 */

package com.telefonica.iot.cygnus.sinks;

import com.telefonica.iot.cygnus.backends.postgresql.PostgreSQLBackend;
import com.telefonica.iot.cygnus.containers.NotifyContextRequest;
import com.telefonica.iot.cygnus.containers.NotifyContextRequest.ContextAttribute;
import com.telefonica.iot.cygnus.containers.NotifyContextRequest.ContextElement;
import com.telefonica.iot.cygnus.containers.NotifyContextRequest.ContextElementResponse;
import com.telefonica.iot.cygnus.errors.CygnusBadConfiguration;
import com.telefonica.iot.cygnus.log.CygnusLogger;
import com.telefonica.iot.cygnus.utils.Constants;
import com.telefonica.iot.cygnus.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.flume.Context;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author cristobalcastillo
 *
 * Custom PostgreSQL sink for Orion Context Broker. The PostgreSQL design for this sink is:
 *  - There is a database per user, being its attrName:
 *    cygnus_<username>
 *  - Each entity has its data stored in a specific table, being its attrName:
 *    cygnus_<entity_id>_<entity_type>
 *  - Each event data is stored in the appropriate table as a new row, having each row the following fields:
 *    recvTimeTs, recvTime, entityId, entityType, attrName, attrType, attrValue
 * 
 * As can be seen, a table is created per each entity, containing all the historical values this entity's attributes
 * have had.
 * 
 * It is important to note that certain degree of reliability is achieved by using a rolling back mechanism in the
 * channel, i.e. an event is not removed from the channel until it is not appropriately persisted.
 */
public class OrionPostgreSQLSink extends OrionSink {
        
    private static final CygnusLogger LOGGER = new CygnusLogger(OrionPostgreSQLSink.class);
    private String postgresHost;
    private String postgresPort;
    private String postgresUsername;
    private String postgresPassword;
    private boolean rowAttrPersistence;
    private PostgreSQLBackend persistenceBackend;
    
    /**
     * Constructor.
     */
    public OrionPostgreSQLSink() {
        super();
    } // OrionPostgreSQLSink
    
    /**
     * Gets the PostgreSQL host. It is protected due to it is only required for testing purposes.
     * @return The postgreSQL host
     */
    protected String getPostgresHost() {
        return postgresHost;
    } // getPostgresHost
    
    /**
     * Gets the PostgreSQL port. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL port
     */
    protected String getPostgresPort() {
        return postgresPort;
    } // getPostgresPort
    
    /**
     * Gets the PostgreSQL username. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL username
     */
    protected String getPostgresUsername() {
        return postgresUsername;
    } // getPostgresUsername
    
    /**
     * Gets the PostgreSQL password. It is protected due to it is only required for testing purposes.
     * @return The PostgreSQL password
     */
    protected String getPostgresPassword() {
        return postgresPassword;
    } // getPostgresPassword
    
    /**
     * Returns if the attribute persistence is row-based. It is protected due to it is only required for testing
     * purposes.
     * @return True if the attribute persistence is row-based, false otherwise
     */
    protected boolean getRowAttrPersistence() {
        return rowAttrPersistence;
    } // getRowAttrPersistence

    /**
     * Returns the persistence backend. It is protected due to it is only required for testing purposes.
     * @return The persistence backend
     */
    protected PostgreSQLBackend getPersistenceBackend() {
        return persistenceBackend;
    } // getPersistenceBackend
    
    /**
     * Sets the persistence backend. It is protected due to it is only required for testing purposes.
     * @param persistenceBackend
     */
    protected void setPersistenceBackend(PostgreSQLBackend persistenceBackend) {
        this.persistenceBackend = persistenceBackend;
    } // setPersistenceBackend
    
    @Override
    public void configure(Context context) {
        postgresHost = context.getString("postgres_host", "localhost");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgres_host=" + postgresHost + ")");
        postgresPort = context.getString("postgres_port", "5432");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgres_port=" + postgresPort + ")");
        postgresUsername = context.getString("postgres_username", "opendata");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgres_username=" + postgresUsername + ")");
        // FIXME: cosmosPassword should be read as a SHA1 and decoded here
        postgresPassword = context.getString("postgres_password", "unknown");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (postgres_password=" + postgresPassword + ")");
        rowAttrPersistence = context.getString("attr_persistence", "row").equals("row");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (attr_persistence="
                + (rowAttrPersistence ? "row" : "column") + ")");
    } // configure

    @Override
    public void start() {
        // create the persistence backend
        LOGGER.debug("[" + this.getName() + "] PostgreSQL persistence backend created");
        persistenceBackend = new PostgreSQLBackend(postgresHost, postgresPort, postgresUsername, postgresPassword);
        super.start();
        LOGGER.info("[" + this.getName() + "] Startup completed");
    } // start

    @Override
    void persist(Map<String, String> eventHeaders, NotifyContextRequest notification) throws Exception {
        // get some header values
        Long recvTimeTs = new Long(eventHeaders.get("timestamp"));
        String fiwareService = eventHeaders.get(Constants.HEADER_SERVICE);
        String[] fiwareServicePaths = eventHeaders.get(Constants.HEADER_SERVICE_PATH).split(",");
        String[] destinations = eventHeaders.get(Constants.DESTINATION).split(",");

        // human readable version of the reception time
        String recvTime = Utils.getHumanReadable(recvTimeTs, false);

        // create the database for this fiwareService if not yet existing... the cost of trying to create it is the same
        // than checking if it exits and then creating it
        String dbName = buildDbName(fiwareService);
        
        // the database can be automatically created both in the per-column or per-row mode; anyway, it has no sense to
        // create it in the per-column mode because there will not be any table within the database
        if (rowAttrPersistence) {
            persistenceBackend.createDatabaseSchema(dbName);
        } // if
        
        // iterate on the contextResponses
        ArrayList contextResponses = notification.getContextResponses();
        
        for (int i = 0; i < contextResponses.size(); i++) {
            // get the i-th contextElement
            NotifyContextRequest.ContextElementResponse contextElementResponse = (NotifyContextRequest.ContextElementResponse) contextResponses.get(i);
            NotifyContextRequest.ContextElement contextElement = contextElementResponse.getContextElement();
            String entityId = contextElement.getId();
            String entityType = contextElement.getType();
            LOGGER.debug("[" + this.getName() + "] Processing context element (id=" + entityId + ", type= "
                    + entityType + ")");
            
            // build the table name
            String tableName = buildTableName(fiwareServicePaths[i], destinations[i]);
            
            // if the attribute persistence is based in rows, create the table where the data will be persisted, since
            // these tables are fixed 7-field row ones; otherwise, the size of the table is unknown and cannot be
            // created in execution time, it must be previously provisioned
            if (rowAttrPersistence) {
                // create the table for this entity if not existing yet... the cost of trying yo create it is the same
                // than checking if it exits and then creating it
                persistenceBackend.createTable(dbName, tableName);
            } // if
            
            // iterate on all this entity's attributes, if there are attributes
            ArrayList<NotifyContextRequest.ContextAttribute> contextAttributes = contextElement.getAttributes();
            
            if (contextAttributes == null || contextAttributes.isEmpty()) {
                LOGGER.warn("No attributes within the notified entity, nothing is done (id=" + entityId
                        + ", type=" + entityType + ")");
                continue;
            } // if
            
            // this is used for storing the attribute's names and values when dealing with a per column attributes
            // persistence; in that case the persistence is not done attribute per attribute, but persisting all of them
            // at the same time
            HashMap<String, String> attrs = new HashMap<String, String>();
            
            // this is used for storing the attribute's names (sufixed with "-md") and metadata when dealing with a per
            // column attributes persistence; in that case the persistence is not done attribute per attribute, but
            // persisting all of them at the same time
            HashMap<String, String> mds = new HashMap<String, String>();

            for (NotifyContextRequest.ContextAttribute contextAttribute : contextAttributes) {
                String attrName = contextAttribute.getName();
                String attrType = contextAttribute.getType();
                String attrValue = contextAttribute.getContextValue(false);
                String attrMetadata = contextAttribute.getContextMetadata();
                LOGGER.debug("[" + this.getName() + "] Processing context attribute (name=" + attrName + ", type="
                        + attrType + ")");
                
                if (rowAttrPersistence) {
                    LOGGER.info("[" + this.getName() + "] Persisting data at OrionPostgreSQLSink. Database: " + dbName
                            + ", Table: " + tableName + ", Data: " + recvTimeTs / 1000 + "," + recvTime + ","
                            + entityId + "," + entityType + "," + attrName + "," + entityType + "," + attrValue + ","
                            + attrMetadata);
                    persistenceBackend.insertContextData(dbName, tableName, recvTimeTs / 1000, recvTime,
                            entityId, entityType, attrName, attrType, attrValue, attrMetadata);
                } else {
                    attrs.put(attrName, attrValue);
                    mds.put(attrName + "_md", attrMetadata);
                } // if else
            } // for
            
            // if the attribute persistence mode is per column, now is the time to insert a new row containing full
            // attribute list of attrName-values.
            if (!rowAttrPersistence) {
                LOGGER.info("[" + this.getName() + "] Persisting data at OrionPostgreSQLSink. Database: " + dbName
                        + ", Table: " + tableName + ", Timestamp: " + recvTime + ", Data (attrs): " + attrs.toString()
                        + ", (metadata): " + mds.toString());
                persistenceBackend.insertContextData(dbName, tableName, recvTime, attrs, mds);
            } // if
        } // for
    } // persist
    
    /**
     * Builds a database name given a fiwareService. It throws an exception if the naming conventions are violated.
     * @param fiwareService
     * @return
     * @throws Exception
     */
    private String buildDbName(String fiwareService) throws Exception {
        String dbName = fiwareService;
        
        if (dbName.length() > Constants.MAX_NAME_LEN) {
            throw new CygnusBadConfiguration("Building dbName=fiwareService (" + dbName + ") and its length is greater "
                    + "than " + Constants.MAX_NAME_LEN);
        } // if
        
        return dbName;
    } // buildDbName
    
    /**
     * Builds a package name given a fiwareServicePath and a destination. It throws an exception if the naming
     * conventions are violated.
     * @param fiwareServicePath
     * @param destination
     * @return
     * @throws Exception
     */
    private String buildTableName(String fiwareServicePath, String destination) throws Exception {
        String tableName;
                
        if (fiwareServicePath.length() == 0) {
            tableName = destination;
        } else {
            tableName = fiwareServicePath + '_' + destination;
        } // if else

        if (tableName.length() > Constants.MAX_NAME_LEN) {
            throw new CygnusBadConfiguration("Building tableName=fiwareServicePath + '_' + destination (" + tableName
                    + ") and its length is greater than " + Constants.MAX_NAME_LEN);
        } // if
        
        return tableName;
    } // buildTableName

} // OrionPostgreSQLSink