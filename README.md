# jshell-server
A http server running more JShells

## prerequisites

- Java 1.9
- Docker
- single available port
- least 4GB memory

## backend container feature

- bidirectional communication
- supporting secret key for security

## frontend server feature

- one server to many containers
- multiplexing communication for using many shells
- stable shell lifecycle
- standalone mode with play
