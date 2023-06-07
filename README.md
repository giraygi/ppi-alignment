# Parallel Exchange of Randomized Subgraphs for Optimizing Network Alignment (PERSONA)
The project was developed with Eclipse IDE and maven buid automation tool. Main Class is in CLIPPI. Run CLIPPI with -h or --help arguments to see the possible list of arguments.
The code documentation and good programming practices will be revised after the paper has been reviewed.

A neo4j instance should be activated before running the application. The neo4j password can be given by the respective password argument in the application. In the previous versions the password was simply set to "evet"

## Requirements
- JDK 8
- Neo4J 3.5
- Maven

## Example call
`java typed.ppi.CLIPPI /usr/lib/jvm/java-8-openjdk-amd64/bin/java -XX:MaxDirectMemorySize=64000000000 -Dfile.encoding=UTF-8 -classpath maven-jars/ -c -cc -po -n1 dmela.txt -n2 celeg.txt -s dmce.sim -i1 dmela.net -i2 celeg.net -a1 dmela.annos -a2 celeg.annos -i -ic 50 -sv -e alignment -p /home/giray/Desktop/dmceisobasetime -lo -epp -gopp -l dmce_isbs_time -ppc 10`


## Recommendations
- Set XX:MaxDirectMemorySize parameter of java runtime to a value bigger than the physical RAM size.
- Seperate the database forming process (especially due to the huge 'compute powers' task) with the interactive alignment process. Namely, it is a good practice to store the database for skipping future database creation tasks.
- Care to pick the appropriate custom aligners when you run a population size smaller than 10.
- Consider a smaller population size or database tuning in case of performance issues.
- Implement alternative custom aligners if you prefer a population size bigger than 10.

