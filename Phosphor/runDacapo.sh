#!/bin/bash
DACAPO_DIR=dacapo
PHOSPHOR_JAR=`pwd`/target/Phosphor-0.0.2-SNAPSHOT.jar
BENCHMARKS=(avrora batik eclipse fop h2 jython luindex lusearch pmd sunflow tomcat tradebeans tradesoap xalan) #tradebeans tradesoap are disabled in this script because of the PITA with distributing the openjdk jce.jar file with everything, then switching it in...
#if you want to run them, copy the jce.jar file from an openjdk distribution of the same version into your hotspot jvm that you are going to instrument before instrumenting it.
# (export controls are annoying)

HAD_ERROR=0
if [ ! -d "$DACAPO_DIR" ]; then
echo "Downloading dacapo jar";
mkdir dacapo;
cd dacapo;
wget --quiet http://ase.cs.columbia.edu:8282/repository/internal/org/dacapo/dacapo/9.12bach/dacapo-9.12bach.jar;
unzip dacapo-9.12bach.jar;
cd ..;
fi
    echo "Ensuring instrumented dacapo exist for tests... to refresh, do mvn clean\n";
    if [ ! -d "target/dacapo-inst-int" ]; then
    echo "Creating int tag instrumented dacapo";
    java -Xmx6g -XX:MaxPermSize=512m -jar $PHOSPHOR_JAR $DACAPO_DIR target/dacapo-inst-int;
    else
    echo "Not regenerating int tag instrumented dacapo";
    fi
    if [ ! -d "target/dacapo-inst-obj" ]; then
    echo "Creating obj tag instrumented dacapo";
    java -Xmx6g -XX:MaxPermSize=512m  -jar $PHOSPHOR_JAR -multiTaint $DACAPO_DIR target/dacapo-inst-obj;
    else
    echo "Not regenerating obj tag instrumented dacapo";
    fi
    if [ ! -d "target/dacapo-inst-singlearraytag" ]; then
    echo "Creating obj tag single array tag instrumented dacapo";
    java -Xmx6g -XX:MaxPermSize=512m  -jar $PHOSPHOR_JAR -singleArrayTag -multiTaint $DACAPO_DIR target/dacapo-inst-obj-singlearraytag
    else
    echo "Not regenerating obj tag instrumented dacapo w single array tag";
    fi

#    if [ ! -d "target/dacapo-inst-implicit" ]; then
#    echo "Creating obj tag + implicit flow instrumented dacapo\n";
#    java -Xmx6g -XX:MaxPermSize=512m -jar $PHOSPHOR_JAR -controlTrack -multiTaint $DACAPO_DIR target/dacapo-inst-implicit;
#    else
#    echo "Not regenerating implicit flow instrumented dacapo\n";
#    fi
echo "Trying int tag benchmarks. Note: these numbers will not be super accurate from travis!";
for bm in "${BENCHMARKS[@]}"
do
echo "$JAVA_HOME/bin/java -Xmx8g -XX:MaxPermSize=1g -cp $DACAPO_DIR Harness $bm"
$JAVA_HOME/bin/java -Xmx8g -XX:MaxPermSize=1g -cp $DACAPO_DIR Harness $bm
echo "target/jre-inst-int/bin/java -Xmx8g -XX:MaxPermSize=1g -Xbootclasspath/p:$PHOSPHOR_JAR -javaagent:$PHOSPHOR_JAR -cp target/dacapo-inst-int/ -Declipse.java.home=$JAVA_HOME/jre/ Harness $bm"
target/jre-inst-int/bin/java -Xmx8g -XX:MaxPermSize=1g -Xbootclasspath/p:$PHOSPHOR_JAR -javaagent:$PHOSPHOR_JAR -cp target/dacapo-inst-int/ -Declipse.java.home=$JAVA_HOME/jre/ Harness $bm
echo "target/jre-inst-obj/bin/java -Xmx8g -XX:MaxPermSize=1g -Xbootclasspath/p:$PHOSPHOR_JAR -javaagent:$PHOSPHOR_JAR -cp target/dacapo-inst-obj/ -Declipse.java.home=$JAVA_HOME/jre/ Harness $bm"
target/jre-inst-obj/bin/java -Xmx8g -XX:MaxPermSize=1g -Xbootclasspath/p:$PHOSPHOR_JAR -javaagent:$PHOSPHOR_JAR -cp target/dacapo-inst-obj/ -Declipse.java.home=$JAVA_HOME/jre/ Harness $bm

echo "target/jre-inst-obj-singlearray/bin/java -Xmx8g -XX:MaxPermSize=1g -Xbootclasspath/p:$PHOSPHOR_JAR -javaagent:$PHOSPHOR_JAR -cp target/dacapo-inst-obj-singlearraytag/ -Declipse.java.home=$JAVA_HOME/jre/ Harness $bm"
target/jre-inst-obj-singlearray/bin/java -Xmx8g -XX:MaxPermSize=1g -Xbootclasspath/p:$PHOSPHOR_JAR -javaagent:$PHOSPHOR_JAR=singlearraytag -cp target/dacapo-inst-obj-singlearraytag/ -Declipse.java.home=$JAVA_HOME/jre/ Harness $bm
if [ $? -ne 0 ]; then
HAD_ERROR=`expr $HAD_ERROR + 1`
fi

done
echo "Total errors: $HAD_ERROR"
if [ $HAD_ERROR -ne 0 ]; then
exit 255
fi
