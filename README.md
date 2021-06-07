##### The program is implemented in Java. It uses the consistent hashing and multicast techniques to implement such a game:
```
Players are connected in a ring: A is bombing B who is bombing C, ... who is bombing Z who is bombing A.

Each player has a 10x10 grid of "dots" where one "single-dot ship" is positioned randomly. A player loses
if this ship is bombed.

Players cannot see each other's grids directly. Each player randomly selects a dot location on the enemy
grid to bomb and sends the bomb to the enemy. If the bomb lands in the enemy's dot-ship, the enemy dies;
otherwise, it lives. When a player dies, relevant neighbors are matched up so that their unfinished games
can continue.

For example: A is bombing B is bombing C is bombing D is bombing A. If B hits C's ship, B wins, C loses/dies.
B is now bombing D.
```

##### It is a multi-threaded program. We run the command "java -jar folder-name/BattleDot-1.0-SNAPSHOT.jar" to launch the program. Each process has an uid.

##### Each process can communicate with other processes to create a game group in a small local area network (NOTE: multicast cannot be disabled. If your firewall disables multicast, you need to change your firewall configuration, or temporarily stop the firewall). The first process needs to wait for other processes.

##### When there are at least 2 processes, we can begin the game.

##### We input "#row #col" (such as "5 2") and then press Enter to attack our target.

##### When there are at least 3 processes, we can input "#uid1 #uid2" plus the Enter key to relocate one process in the circle.


#### How to compile the Java code:
* internet access;
* install JDK 15;
* install Apache Maven (3.6.3+);
* run the command "mvn package" under the folder of the project.


#### OS:
* we can run it in Linux and Windows.


#### Log file:
* when we run the command, if the current working folder is current-working-folder, you may find the log files under current-working-folder/logs


#### WIN or LOSE:
* If we find the "GAME OVER" string in the console, we lose. The last survivor is the winner.
