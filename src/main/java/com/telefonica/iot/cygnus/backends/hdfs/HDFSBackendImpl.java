/**
 * Copyright 2015 Telefonica Investigación y Desarrollo, S.A.U
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

package com.telefonica.iot.cygnus.backends.hdfs;

import com.telefonica.iot.cygnus.backends.hive.HiveBackend;
import com.telefonica.iot.cygnus.backends.http.HttpBackend;
import com.telefonica.iot.cygnus.errors.CygnusPersistenceError;
import com.telefonica.iot.cygnus.log.CygnusLogger;
import com.telefonica.iot.cygnus.utils.Constants;
import com.telefonica.iot.cygnus.utils.Utils;
import java.util.ArrayList;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;

/**
 *
 * @author frb
 * 
 * HDFS persistence based on the HttpFS service (TCP/14000). HttpFS is an alternative implementation of the WebHDFS
 * API which hides the cluster details by forwarding directly to the Master node instead of to the Data node.
 */
public class HDFSBackendImpl extends HttpBackend implements HDFSBackend {
    
    private final String hdfsUser;
    private final String hdfsPassword;
    private final String hiveHost;
    private final String hivePort;
    private final boolean serviceAsNamespace;
    private static final CygnusLogger LOGGER = new CygnusLogger(HDFSBackendImpl.class);
    private static final String BASE_URL = "/webhdfs/v1/user/";
    
    /**
     * 
     * @param hdfsHosts
     * @param hdfsPort
     * @param hdfsUser
     * @param hiveHost
     * @param hdfsPassword
     * @param hivePort
     * @param krb5
     * @param krb5User
     * @param krb5Password
     * @param krb5LoginConfFile
     * @param krb5ConfFile
     * @param serviceAsNamespace
     */
    public HDFSBackendImpl(String[] hdfsHosts, String hdfsPort, String hdfsUser, String hdfsPassword, String hiveHost,
            String hivePort, boolean krb5, String krb5User, String krb5Password, String krb5LoginConfFile,
            String krb5ConfFile, boolean serviceAsNamespace) {
        super(hdfsHosts, hdfsPort, krb5, krb5User, krb5Password, krb5LoginConfFile, krb5ConfFile);
        this.hdfsUser = hdfsUser;
        this.hdfsPassword = hdfsPassword;
        this.hiveHost = hiveHost;
        this.hivePort = hivePort;
        this.serviceAsNamespace = serviceAsNamespace;
    } // HDFSBackendImpl
   
    @Override
    public void createDir(String dirPath) throws Exception {
        String relativeURL = BASE_URL + (serviceAsNamespace ? "" : (hdfsUser + "/")) + dirPath
                + "?op=mkdirs&user.name=" + hdfsUser;
        HttpResponse response = doRequest("PUT", relativeURL, true, null, null);

        // check the status
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new CygnusPersistenceError("The /user/" + (serviceAsNamespace ? "" : (hdfsUser + "/"))
                    + dirPath + " directory could not be created in HDFS. HttpFS response: "
                    + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
        } // if
    } // createDir
    
    @Override
    public void createFile(String filePath, String data)
        throws Exception {
        String relativeURL = BASE_URL + (serviceAsNamespace ? "" : (hdfsUser + "/")) + filePath
                + "?op=create&user.name=" + hdfsUser;
        HttpResponse response = doRequest("PUT", relativeURL, true, null, null);
        
        // check the status
        if (response.getStatusLine().getStatusCode() != 307) {
            throw new CygnusPersistenceError("The /user/" + (serviceAsNamespace ? "" : (hdfsUser + "/"))
                    + filePath + " file could not be created in HDFS. HttpFS response: "
                    + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
        } // if
        
        // get the redirection location
        Header header = response.getHeaders("Location")[0];
        String absoluteURL = header.getValue();

        // do second step
        ArrayList<Header> headers = new ArrayList<Header>();
        headers.add(new BasicHeader("Content-Type", "application/octet-stream"));
        response = doRequest("PUT", absoluteURL, false, headers, new StringEntity(data + "\n"));
    
        // check the status
        if (response.getStatusLine().getStatusCode() != 201) {
            throw new CygnusPersistenceError("/user/" + (serviceAsNamespace ? "" : (hdfsUser + "/"))
                    + filePath + " file created in HDFS, but could not write the data. HttpFS response: "
                    + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
        } // if
    } // createFile
    
    @Override
    public void append(String filePath, String data) throws Exception {
        String relativeURL = BASE_URL + (serviceAsNamespace ? "" : (hdfsUser + "/")) + filePath
                + "?op=append&user.name=" + hdfsUser;
        HttpResponse response = doRequest("POST", relativeURL, true, null, null);

        // check the status
        if (response.getStatusLine().getStatusCode() != 307) {
            throw new CygnusPersistenceError("The /user/" + (serviceAsNamespace ? "" : (hdfsUser + "/"))
                    + filePath + " file seems to not exist in HDFS. HttpFS response: "
                    + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
        } // if

        // get the redirection location
        Header header = response.getHeaders("Location")[0];
        String absoluteURL = header.getValue();

        // do second step
        ArrayList<Header> headers = new ArrayList<Header>();
        headers.add(new BasicHeader("Content-Type", "application/octet-stream"));
        response = doRequest("POST", absoluteURL, false, headers, new StringEntity(data + "\n"));
        
        // check the status
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new CygnusPersistenceError("/user/" + (serviceAsNamespace ? "" : (hdfsUser + "/"))
                    + filePath + " file exists in HDFS, but could not write the data. HttpFS response: "
                    + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
        } // if
    } // append
    
    @Override
    public boolean exists(String filePath) throws Exception {
        String relativeURL = BASE_URL + (serviceAsNamespace ? "" : (hdfsUser + "/")) + filePath
                + "?op=getfilestatus&user.name=" + hdfsUser;
        HttpResponse response = doRequest("GET", relativeURL, true, null, null);

        // check the status
        return (response.getStatusLine().getStatusCode() == 200);
    } // exists
    
    /**
     * Provisions a Hive external table (row mode).
     * @param dirPath
     * @throws Exception
     */
    public void provisionHiveTable(String dirPath) throws Exception {
        // get the table name to be created
        // the replacement is necessary because Hive, due it is similar to MySQL, does not accept '-' in the table names
        String tableName = Utils.encodeHive((serviceAsNamespace ? "" : hdfsUser + "_") + dirPath) + "_row";
        LOGGER.info("Creating Hive external table=" + tableName);
        
        // get a Hive client
        HiveBackend hiveClient = new HiveBackend(hiveHost, hivePort, hdfsUser, hdfsPassword);

        // create the standard 8-fields
        String fields = "("
                + Constants.RECV_TIME_TS + " bigint, "
                + Constants.RECV_TIME + " string, "
                + Constants.ENTITY_ID + " string, "
                + Constants.ENTITY_TYPE + " string, "
                + Constants.ATTR_NAME + " string, "
                + Constants.ATTR_TYPE + " string, "
                + Constants.ATTR_VALUE + " string, "
                + Constants.ATTR_MD + " array<string>"
                + ")";

        // create the query
        
        String query = "create external table " + tableName + " " + fields + " row format serde "
                + "'org.openx.data.jsonserde.JsonSerDe' location '/user/" + (serviceAsNamespace ? "" : (hdfsUser + "/"))
                + dirPath + "'";

        // execute the query
        if (!hiveClient.doCreateTable(query)) {
            LOGGER.warn("The HiveQL external table could not be created, but Cygnus can continue working... "
                    + "Check your Hive/Shark installation");
        } // if
    } // provisionHiveTable
    
    /**
     * Provisions a Hive external table (column mode).
     * @param dirPath
     * @param fields
     * @throws Exception
     */
    public void provisionHiveTable(String dirPath, String fields) throws Exception {
        // get the table name to be created
        // the replacement is necessary because Hive, due it is similar to MySQL, does not accept '-' in the table names
        String tableName = Utils.encodeHive((serviceAsNamespace ? "" : hdfsUser + "_") + dirPath) + "_column";
        LOGGER.info("Creating Hive external table=" + tableName);
        
        // get a Hive client
        HiveBackend hiveClient = new HiveBackend(hiveHost, hivePort, hdfsUser, hdfsPassword);
        
        // create the query
        String query = "create external table " + tableName + " (" + fields + ") row format serde "
                + "'org.openx.data.jsonserde.JsonSerDe' location '/user/" + (serviceAsNamespace ? "" : (hdfsUser + "/"))
                + dirPath + "'";

        // execute the query
        if (!hiveClient.doCreateTable(query)) {
            LOGGER.warn("The HiveQL external table could not be created, but Cygnus can continue working... "
                    + "Check your Hive/Shark installation");
        } // if
    } // provisionHiveTable
    
} // HDFSBackendImpl
