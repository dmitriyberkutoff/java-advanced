@echo off

set root=%~dp0\..\..
set ja2023=%root%\java-advanced-2023
set modules=%ja2023%\modules
set cp1=%modules%\info.kgeorgiy.java.advanced.implementor
set cp2=%modules%\info.kgeorgiy.java.advanced.base
set cp3=%ja2023%\lib\junit-4.11.jar
set implementor=%~dp0\..\java-solutions\info\kgeorgiy\ja\berkutov\implementor\Implementor.java

javadoc -private -classpath "%cp1%;%cp2%;%cp3%" -d %~dp0\..\javadoc %implementor%