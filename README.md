# findStrings

Finds Strings in packed source code files.

## Problem statement

A set of files need to be checked for the occurence of a given set of Strings.
The source files are contained in ZIP files that can contain ZIP files recursively


## Use

`java -jar findString.jar -d directory -s strings [-o output]`

- -d,--dir <arg>          directory with all zip files
- -s,--stringfile <arg>   Filename with Strings to search, one per line
- -o,--dir <arg>          Output file name for report in MD format
- -nz,--nz                Rerun find operation on a ready unzipped structure - good for alternate finds

(C) 2019 St.Wissel - see license file

