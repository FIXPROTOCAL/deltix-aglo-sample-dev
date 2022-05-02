## Sample algorithm

This project illustrates how to build, test, debug, and deploy algorithms for Deltix Execution Server.
This archive contains IntelliJ/IDEA project and Gradle build files for ICEBERG sample algorithm.

See Algorithm Developer's Guide for more information.

### Build
Edit `gradle.properties` and correct location of Execution Server installation (or repository):
```
emberdir=D:/projects/deltix/ember
```

### Test
This project includes two kinds of unit tests for algorithm:

* Test_IcebergAlgorithmMock gives you an ability to mock various input messages (order replacements, market data, etc) 
  and verifies algorithm outputs.

* Test_IcebergAlgorithmFuzzy "fuzzy" test - bombards algorithms with random inputs and verifies if any assertions or 
  exceptions happen.

### Debug

One simple way to debug your algorithm is running entire Execution Server under debugger. 
Take a look at `Ember Server` Run configuration. It uses `deltix.ember.app.EmberApp` as a main class and `ember.home` 
system property that point to ember configuration home.
You can just setup breakpoints in your algorithm and launch debugger.

See ES Quick Start document for more information.

```
algorithms {
  ICEBERG: ${template.algorithm.default} {
    factory = "deltix.ember.samples.algorithm.iceberg.IcebergSampleAlgorithmFactory"
  }
}
````

### Deploy

Entire Gradle project uses java-library plugin.

```
gradlew build 
```

The build produces `build/lib/algorithm-sample-*.jar`. To deploy your algorithm to actual server copy this JAR file under `lib/custom/` directory of your ES installation.
The last step is to define your algorithm in server's `ember.conf` (just like we did in Debug section).  

You can also run OrderSubmitSample to see how you can send requests to your algorithm.

This package includes Ansible playbook located under `distribution/ansible` folder. It can be used to deploy Execution Server and all pre-requisites on a clean Linux server.


See Algorithm Developer's Guide for more information.  


## Note

1. Installer:
   Download QuantServer installer here:
   https://deltix-installers.s3.eu-west-3.amazonaws.com/5.4/deltix-windows-installer-online-5.4.78.jar
2. Download template home (used post installation). Extract zip into folder.
   https://deltix-installers.s3.eu-west-3.amazonaws.com/Temp/QuantServerHome.zip
   This home contains securities definition for Coinbase (BTC/USD, ETH/USD, etc) as well as aggregator
   process definition file to aggregate level 2 data into Timebase stream called "COINBASE".
   Timebase runs on port 8011.
   ember.conf should point to this timebase instance and COINBASE should be used for stream.
3. Use license key during installation process: 4746-18043582174-D201
4. Once completed, launch QuantServer Architect and open the folder you previously extracted. Folder structure should be:
   QuantServerHome:
   --- config
   --- logs
   --- services
   --- timebase

Open QuantServerHome folder using the architect client. Once open, hover over Timebase block and use
right mouse button - Start in Console.
This will start the timebase service and start aggregataing market data from COINBASE exchange.


- set path ember on your comuter and gradle.properties and java
- start Timebase.bat
- start ember 
- start ember-monitor  (localhost:8988)
