# compiler-micro-java
Implementation of a compiler for a simplified version of Java, done as a school assignment.

The specification of the language can be found on the child links from [this folder](http://ir4pp1.etf.rs/Domaci/2020-2021/) of the course website.

## External tools used

- Lexical analysis was performed using the JFlex library.
The JFlex.jar can be downloaded from the [project website](https://www.jflex.de/).

- Syntax analysis was performed using the CUP library.
The cup_v10k.jar was downloaded from the [course website](http://ir4pp1.etf.rs/Domaci.html).
Note that this version of the CUP library is based on v0.10 version.
The user manual for that version can be found on the [project website](https://www.cs.princeton.edu/~appel/modern/java/CUP/manual.html).
The CUP library is now maintained by the Technical University of Munich, and the new project page can be found [here](http://www2.cs.tum.edu/projects/cup/).
