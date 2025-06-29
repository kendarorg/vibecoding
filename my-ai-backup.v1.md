All prompts are BRAVE!

### Prompt 001

Should create a java system to backup and synchronize files from a client to server
* The client and server will communicate via TCP protocol
* Multiple clients can connect at once, but only one client can access a single backup target location
* The maximum packet size will be in server settings
* The packet fields will be the following
	* Integer: length of the packet including itself
	* Integer: The connection id (used to parallelize the communication. Starts from 0 for the connection )
  * UUID: The session id (defined in the connection phase, is unique for the whole session)
  * Integer: packet id used when sending data in multiple blocks
  * char[2]: containing the type of message
  * byte[]: containing the zipped content of the message. Can be anything needed
* The handshake and termination of the process will be on the same connection
* The client, will create multiple TCP connections to optimize network usage during transfers
* The server will not store previous versions of the files
* If the connection is interrupted the partially uploaded files will be removed
* The functionalities will be
  * Backup/Restore without deleting old files
  * Backup/Restore deleting the files not present on source
  * Backup/Restore without deleting old files with "date separated strucuture" on backup
    * Doing backup will get the date of the file and create a directory strucure based on dates
    * Doing backup Inside the single date the original directory strucuture will be kept
    * Doing restore will place the files directly in the target directory discarding the dates
* The project will be composed by three modules
  * sync-lib: With the protocol definition and common parts
  * sync-client: The client, runnable from command line
  * sync-server: The server, runnable from command line
* The server will have a settings.json containing
  * The listening port of the server
  * The maximum packet size
  * The maximum parallel TCP connections for a single session
  * The list of uniqueid, user, password (and weather they are admin or not)
  * The target folders with 
    * "virtual" name
    * Real path
    * Type of backup
    * Id of the users allowed to backup to/restore from
* The protocol flows will be
  * Backup
    * Client Connect with login, password, and "virtual" target
    * Server Accept or refuse the connection checking the settings for the folder and permissions
    * Client send the list of files, the maximum packet size and the maximum parallel TCP connections allowed
      * This can be splitted in multiple packets if exceeding the maximum packet size
    * Server 
      * check what files will need and eventually remove the files non existing on client
      * send back the list of files that are missing
      * This can be splitted in multiple packets if exceeding the maximum packet size
    * Client create the parallel connections and  
    * For each file
      * Client send the descriptor of the file (name,path,size,modification time, creation time)
      * Server create (eventually) the directory according to the backup type and Acknowledge
      * Client send the file content (eventually in multiple blocks), reading a max of "maximum packet size". This will result in a packet smaller than maximum packet size because of the zipping of content
      * Server 
        * Append the data to the target file ( or create it if this is the packet 0)
        * Set the modification and creation time to "1970-01-01 00:00:00" to force rewriting
      * Client send the End of file packet
      * Server set the real modification time and creation time from the client and acknowledge
    * Client, When all ack are received for all files, send the "End of sync" packet
    * Server receives the message and disconnect
  * Restore  
    * Client Connect with login, password, and "virtual" target
    * Server Accept or refuse the connection checking the settings for the folder and permissions
    * Client send the list of files, the maximum packet size and the maximum parallel TCP connections allowed
      * This can be splitted in multiple packets if exceeding the maximum packet size
    * Server 
      * check what files need to update and what need to remove on client 
      * send back the list of files that are missing or must be removed on client
      * This can be splitted in multiple packets if exceeding the maximum packet size
    * Client 
      * create the parallel connections
      * delete the files to remove
    * For each file
      * Client
        * create the directory for the file
        * send the descriptor of the file (name,path,size,modification time, creation time) it needs to download
      * Server send the file content (eventually in multiple blocks), reading a max of "maximum packet size". This will result in a packet smaller than maximum packet size because of the zipping of content
      * Client 
        * Append the data to the target file ( or create it if this is the packet 0)
        * Set the modification and creation time to "1970-01-01 00:00:00" to force rewriting
      * Server send the End of file packet
      * Client set the real modification time and creation time from the client and acknowledge
    * Client, When all ack are received for all files, send the "End of sync" packet
    * Server receives the message and disconnect
* The serve will expose an authenticate REST API
  * Upon login a token will be generate valid for 30 minutes
  * All the REST APIs will require the token inside the X-Auth-Token header
  * The API will allow CRUD operations on
    * Backup folders
    * Users
    * Max threads
    * Max packet size
  * The user will have the roles
    * admin: all access to all backups and all apis
    * usermanager: access only to user manager apis, and associating users with backups
    * user: access only to api to modify its password and login
	
	
### Prompt 002

Do the following modifications
* On sync-lib
  * Add the possibility for a "dry-run" if not present
* On sync-server
  * Create the main class. It should start
    * If no settings file is given create a default one with one administrator user
    * The TCP server and listen to request to backup/restore and transfer files in an async way
    * The web server for configuration
  * Allow the possibility for a "dry-run" to test the operation
  * Use the protocol specified in sync-lib
* On sync-client
  * Create the main class
  * Add the parser for the command line that should allow (with parameters)
    * Choose the target and source folder
    * Choose if should backup or restore
    * Set the backup server address and port
    * Set the login and password for the 
    * Choose if it should do a "dry-run" or a real backup/restore
  * Start the backup/restore
  * Use the protocol specified in sync-lib
  

### Fix 002

Fixed poms properties

```Quota for Pro is 84%```

### Prompt 003 (twice because of network issues)

Generate the unit testes for the projects. If a real storage is needed use as a root the 'target/tests/UniqueId' directory for each test

```Quota for Pro is 77%```
  
### Fix 003

Mostly fixing unit tests and a weird behaviour on file listing

### Prompt 004 

Fix the failing unit tests for the module sync-client, use a fake socket, no real socket allowed in unit tests

### Fix 004 

Fix all the tests by hand AI went in loopholes

### Prompt 005

* Create the backup integration test on sync-server that
	* Setup a source directory with random files and nested dirs
	* Setup a target directory
	* Start the server
	* Start the client and start the backup
	* Verify that everything is backed up
	* Remove some file from the source
	* Start the client and do the restore

```Completely failed```

### Fix 005

* Extract the server to be run programmatically
* Extract the client to be run programmatically
* Modify the test to run with the new approach

### Prompt 006

* Create one class for each backup type to be used in Server.java with implementation and unit test
	
```Quota for Pro is 52%```
Mostly done but without 

### Now with Copilot-Claude

* List all the directories child of directory "source" that match the pattern [0-9]{4}-[0-9]{2}-[0-9]{2}
* List all the files inside a directory recursively

### Fix 007

* Implement most of backup handlers and tests

### Prompt 008

On the SyncClient::performBackup function,  after receiving the FileListResponseMessage should
* Open 10 connections to the server
* Parallelize the sending of the files on the ten connections
* Before exiting the function wait for the completion of the sending of all files

### Fix 008

* Fix parallelization

### Prompt 009

Modify the Server to allow concurrent files sent by SyncClientApp

* This should happen only when the session.isBackup==true
* When receiving a FILE_DESCRIPTOR should store the current file data in session, using the connectionId as index
* When receiving a FILE_DATA or a FILE_END should retrieve the FileInfo from the session, using the connectionId as index

### FIx 009

* FIxing the tests,
* The server,
* Everything

### Prompt 010

In the client instead of sending all data in one block, read in blocks of maxPacketSize and send multiple FileDataMessage

### Fix 010

* Fixing the backup splitted
* Setup the restore splitted

### Prompt 011

As you did for the backup, use maxConnections tcp connections to receive the files when restoring

### Fix 011 

Copy for real

### Manual 012

Added on of my optimized buffers to replace the json serializer

### Prompot 013

Implement the missing method in the open classes extending Message using the ConnectMessage as an Example

### MAnual 013

Adapt the tests and fix the missing serialization of subitems

### Prompt 014

#file:BackupHandler.java #file:DateSeparatedBackupHandler.java #file:MirrorBackupHandler.java #file:PreserveBackupHandler.java 
given these files refactor them to reduce duplication. Here some info
* Move common stuff on BackupHandler
* Inside the handleFileList the sending of the files when doing restore (!isBackup) is very similar. Could do a common function in BackupHandler handling the common parts receiving a lambda with the specific operations 
* Preparing and sending the FileDataMessage should be a common part too.

### Fix 014

All tentative useless, the error is not recognized
Made with human reasoning :)

### Prompt 017

Should add on sync-server module a way to handle hung transfers
* On session 
	* set an AtomicLong lastOperationTimestamp set to 0
	* expose two public function
		* touch() that set the lastOperationTimestamp to now milliseconds+timeout milliseconds
		* isExpired() verify that now < lastOperationTimestamp
* When creating a TcpConnection it will need a session
	* Writing or starting reading from a connection will "touch" the session
* Parallel to the listening thread there will be another thread that will monitor every 10 seconds all the session
	* When the session is expired disconnect it
	
### Peompt 018

#file:SyncClientAppBackupTest.java #file:SyncClientAppBackupTestSimple.java 
Fix the failing tests aligning them with the modifications on client

### Prompt 019

This is part of the creation a two-way synchronization algorithm

* This function must be in the lib, common to client and server
* Create a class StatusAnalyzer that lists all the files in a "base" directory, and write
	* A .lastupdate.log containing the start time of the run
	* a .operation.log containing
		* Creation time
		* Modification time
		* Size
		* Operation (CR for create, MO for modify, DE for delete)
		* Relative path from the "base" directory
* On subsequent runs the StatusAnalyzer should add lines corresponding to what happened to the file since the last run and udpdate the lastupdate.log

### Prompt 020

Add the following function to StatusAnalyzer
* compact: collapse the operation.log keepeing leaving a list of only "CR", create the file .lastcompact.log with the timestamp of the operation
* compare: compares two operation.log and decide what to update/delete to synchronize correctly the directory using the history of files in case of conflict
* Generate the unit tests for the StatusAnalyzer class

### Fix 020

Fix unit tests

### Prompt 021

* When logging operations on .operation.log add as first field the start time of the run (the one written at the beginning in `.lastupdate.log`
* Update the StatusAnalyzerTest accordingly

### Prompt 022

Add a new BackupHandler using the StatusAnalyzer for two-way synchronization
* Server receive a two-way-sync request with the client last update and the list of modifications since the last update
	** Server loads the updates since the client last update 
	** Server delete the fiels it should delete
	** Server respond with the list of files it will send and the files to delete
* Client start to receive the files	to update/delete (following the usual descriptor/data/end protocol already defined in the project)
	** Client modifies the .operations.log accordingly
* The Client start to send update files to server(following the usual descriptor/data/end protocol already defined in the project)
	** Server modifies the .operations.log accordingly
* Implement the placeholders	

FAILED MISERABLY


### Fix 023

* Refactoring backup/restore process
* Fix the two way sync

### Prompt 024

`#file:AuthController.java` `#file:SettingsController.java`

Given the APIs listed in context 
* Create a login page 
* Create a page to manage users
* Create a page to manage backup folders
	* For each backup folder 
* Create a page to manage the logged in user
* Add the configuration for the static folder
* Allow access to anyone to the login page

### Fix 024

* Restructure the auth (was not working at all)
* Prot client and lib to java 11 (for android compatibility)
  
### Fix 025

* Clean up
* Moved lib to maven
* Added hidden files,system and patterns parameters

### Prompt 026

Generate a FilePattern class that 
* Given a -string- pattern with **, * and ?, following the standard simple patterns for files return true if the input path matches 
* Gienv a string pattern starting wiht @ check if the following regex matches the file

### Prompt 027

* Add to the folders.html, during editing, the following fields
** ignore hidden files (checkbox)
** ignore system files (checkbox)
** ignore patterns (editable list of strings)
* Add the new parameter to the relevant api

### Prompt 028

* Function to create the umask from PosixFileAttributes and viceversa

### Prompt 029

* Collect the id of the running jobs in Server class (with a thread safe structure)
* When the client try to run a job that is alredy running, return an error to the client and disconnect
