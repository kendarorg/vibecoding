### Prompt 001

This is a client application to backup and synchronize to a server
<prerequisites>
* The application need to run in background
* The application need full access to files
* Detect all permissions needed from the following descriptions
* The client implementation for the backup process will be an interface implemented by someone else
</prerequisites>
<application-screens>
* The first screen will list all the backup jobs by name, with a button to delete the job, one to edit and one to show it.
  * On top there will be a "add new job" button
* Show button will show an activity
  * Directory on the phone to backup
  * Address of the remote machine with port
  * Name of the remote target 
  * Last run time
  * Last run duration
  * Next scheduled run
* Edit button will show an activity with
  * Directory on the phone to backup
  * Address of the remote machine with port
  * Directory on the phone to backup (with explore button to choose the directory on the phone
  * Name of the remote target (with explore button that will show a list of available targets)
  * Login 
  * Password
  * Schedule (with an edit button to show an activity with the schedule)
  * If it should run only on wifi
  * If it should run only when charging
* Explore phone directory will show an activity with a tree view where can click on a directory to choose to backup it
  * Each directory will show how many files it contains 
  * Only one dir can be selected
  * Clicking on the top OK button will confirm the chosen directory and set it on the edit activity
* Explore the remote target will require the available targets to the server via a rest api
  * The button will work only when the address of the remote machine, login, password and port are set
  * Only one remote target can be selected
  * The rest api will return a list of string
  * Clicking on the top OK button will confirm the chosen target and set it on the edit activity
* The schedule edit button will give the possibility to run the job
  * Monthly (on specific days)
  * Weekly choosing the days of Week
  * At a certain time 
</application-screens>
<background-operations>
* When the time has come to run a backup 
  * Load all the data for the Backup
  * Call a fake function passing all the backup data to it
  * Wait the end of the backup operation
  * If specified from the settings call the fake stop function when not on wifi or not anymore charging
</background-operations>

GENERATED LOADS OF USELESSS STUFFS

Add the following missing implementations
* ActivityDirectoryExplorerBinding
* ActivityJobEditBinding
* ActivityScheduleEditBinding
* ActivityRemoteTargetExplorerBinding

Generated the xml but not the classes

Took only the permission

GENERATED LOADS OF USELESSS STUFFS (not used)

Fix the compilation problems

GENERATED THE DATA BINDINGS

* Some random classes GENERATED
* Not usable...

Fix the compilation erros

NO GOOD ANSWER

#file:ActivityDirectoryExplorerBinding.java #file:ActivityJobEditBinding.java #file:ActivityRemoteTargetExplorerBinding.java #file:ActivityScheduleEditBinding.java  
the view models are missing

=========================================

RETURNING BACK ON START

Just kept the permissions

### Prompt A01

* Use Java as language
* Given a jobs.json file on the application storage containing a list of object with the following fields
** UUID id
** String name
** String lastExecution
** int lastTransferred
* Create a new fragment, view model, layout in package com.kendar.sync.ui.jobslist that 
** Show the list of items with a button to Edit/Delete/Show the item
** Show a "+" button on top to Add
* If not existing the file will be created and contains an empty list
* Prepare empty fragments for the Add/Edit/Show
* For the Delete ask confirmation

THIS WAS MOSTLY OK

Just had to "merge" the whole thing and add the views on the menus

### Prompt A02

java.lang.IllegalArgumentException: Navigation destination com.kendar.sync:id/addJobFragment referenced from action com.kendar.sync:id/action_jobsListFragment_to_addJobFragment cannot be found from the current destination Destination(com.kendar.sync:id/jobsListFragment) label=Jobs List class=com.kendar.sync.ui.jobslist.JobsListFragment

TOTALLY USELESS

### Prompt A03

#file:AddJobFragment.java #file:fragment_add_job.xml #file:JobsAdapter.java #file:JobsFileUtil.java 

Implement the addition of a job. Should show

* The UUID of the new job random (not changeable)
* A textbox for the Job Name
* A textbox for the Server Address
* A textbox for the Server Port (default to 13856)
* A textbox for the login 
* A textbox for the password
* A textbox with a button to select the Local Source
** Will show the Phone Directory Browser fragment that
*** Will show a textbox to insert a path
*** Pressing ok will set the Local Source to the textbox content
*** Pressing cancel will set the Local Source to null
* A textbox with a button to select the Target Destination
** The button is enabled only when login, password, server address and port are set
** Will show the Remote Target Browser fragment that
*** will call a REST API, assume a POST to /api/test with a Json string "{}" that will return a list of Strings
*** Will show the list of strings and allow to chose one
*** Pressing ok will set the Target Destination to the select string from REST API call
*** Pressing cancel will set the Target Destination to null
* A textbox with a button to select the Schedule Time
** Will show the Schedule fragment that
*** Will show a textbox to insert a string
*** Pressing ok will set the Schedule Time to the textbox content
*** Pressing cancel will set the Schedule Time to null
* A button to save the job
* A button to cancel the operation

Write even the layouts