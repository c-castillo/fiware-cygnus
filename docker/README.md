## Testing cygnus with docker

The only command you need to run is this

	docker-compose -f ./docker/test.yml run --rm cygnus_test

### I just want to build the image

	docker build -f ./docker/Dockerfile.test -t docker_cygnus_test .

NOTE: Be careful not to have any symlinks in your route, as there is a weird bug that does not permit you to build your image (`unable to prepare context: The Dockerfile (/Users/hermanjunge/coding/fiware-cygnus/docker/Dockerfile.test) must be within the build context (.)`)

Links to the bug mentioned above

* [Link 1](https://github.com/docker/docker/issues/14339)
* [Link 2](https://github.com/docker/docker/issues/15642)

### Running the tests

	docker-compose -f ./docker/test.yml run --rm cygnus_test

Will run a shell script, placed in `docker/maven-repo/test.sh`

#### Add maven dependencies

The first time you run this script (`test.sh`), it will download all the maven dependencies, so you will experiment a long wait of > 5 minutes in a good connection. The good thing is that the dependencies will be stored in your directory `./docker/maven-repo`

#### Actually run the tests

(TODO)
	(COMPILING)
	(RUNNING THE TESTS)

### I just want to get inside the testing machine

If you just want to get inside the machine, you can comment all the commands and write `bash`. Leaving the commands in the file as is, will run the tests.
