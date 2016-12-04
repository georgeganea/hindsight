# hindsight

Hindsight is an Eclipse plugin featuring a visualization of the evolution of class accesses.
The source code history of a Java project is traversed as Hindsight extracts a model and stores it into an embedded neo4j https://neo4j.com/ db.
The visualization is show in the Hindsight View and is built using react https://facebook.github.io/react/ and displayed by an Eclipse browser component.

Quick Start Guide

Installation

tl;dr : Download the updatesite zip file org.lrg.updatesite.hindsight.zip from https://github.com/georgeganea/hindsight/releases, install in Eclipse making sure to uncheck "Group items by category".

1. Download the latest release from https://github.com/georgeganea/hindsight/releases

2. Add as an archive updatesite in Eclipse and uncheck "Group items by category".
![Install](readme/install.png?raw=true "Install")

3. Select Hindsight and click Next until installed.
