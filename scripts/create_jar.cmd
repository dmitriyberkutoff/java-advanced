@echo off

set root=%~dp0..\..
set solution=%root%\java-advanced
set shared=%root%\java-advanced-2023
set java_solutions=%solution%\java-solutions
set implementor=%java_solutions%\info\kgeorgiy\ja\berkutov\implementor\Implementor.java
set module_info=%java_solutions%\module-info.java
set manifest=%~dp0\MANIFEST.MF
set class_path=.\info\kgeorgiy\ja\berkutov\implementor\*.class
set artifacts=%root%\java-advanced-2023\artifacts\
set lib=%root%\java-advanced-2023\lib

javac --module-path "%artifacts%;%lib%" %module_info% %implementor% -d out
cd out
jar -cmf %manifest% %~dp0\implementor.jar %class_path%
cd ..
rd /s /q out