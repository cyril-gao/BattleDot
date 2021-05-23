### The program is implemented in Java.

### It is a multi-threaded program. We run the command "java -jar <path>/BattleDot-1.0-SNAPSHOT.jar" to launch the program. Each instance will have an uid.

### Each intance can communicate with other intances to create a game group in a small local area network. The first instance needs to wait for other instances.

### When there are at least 2 instances, we can begin the game.

### We input "#row #col" (such as "5 2") and then press Enter to attack our target.

### When there are at least 3 instances, we can input "#uid1 #uid2" plus the Enter key to relocate ourself in the circle.


### How to compile the Java code:
* internet access;
* install JDK 15;
* install Apache Maven (3.6.3+);
* run the command "mvn package" under the folder of the project.


### OS:
* we can run it in Linux and Windows.


### Log file:
* when we run the command, if the current working folder is <current working folder>, you may find the log files under <current working folder>/logs


### WIN or LOSE:
* If we find the "GAME OVER" string in the console, we lose. The last survivor is the winner.
