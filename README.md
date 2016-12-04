# hindsight

Hindsight is an Eclipse plugin featuring a visualization of the evolution of class accesses.
The source code history of a Java project is traversed as Hindsight extracts a model and stores it into an embedded neo4j https://neo4j.com/ db.
The visualization is show in the Hindsight View and is built using react https://facebook.github.io/react/ and displayed by an Eclipse browser component.

Quick Start Guide

Installation

1. Download the latest release from https://github.com/georgeganea/hindsight/releases

2. Add as an archive updatesite in a fresh Eclipse install http://www.eclipse.org/downloads/ and uncheck "Group items by category".
![Install](readme/install.png?raw=true "Install")

3. Select Hindsight and click Next until installed.


Usage

1. Stop Eclipse and start it from the command line so you can see what Hindsight writes to the console.
2. Make sure to have one single eclipse project in the workspace. Hindsight will soon support multiple projects in the workspace. A small/toy project is recommended for first time users so that it runs fast.
3. Press Ctrl+3 or click in the Quick Action textfield in Eclipses toolbar and type in "GitRun" and press enter. Hindsight will now iterate through all commits and extract a model out of the entire history of the master branch. A progress bar will appear but Hindsight also outputs what it's doing at the console.
![Extract the model](readme/gitrun.png?raw=true "GitRun")
4. Navigate to a class that accesses attributes from other classes and select it in the Java Editor by clicking on the name of the class. Press Ctrl+3 (for OS X it's Cmd+3) and type in "Show Accesses Evolution" and press enter.
![Show the visualization](readme/show.png?raw=true "Show Accesses Evolution")
5. The Hindsight view should now be opened and showing the visualization, if it is not, try repeating the previous step.
6. Clicking on any column of the visualization will open the Diff view for that commit.
![visualization](readme/compare.png?raw=true "Compare")
