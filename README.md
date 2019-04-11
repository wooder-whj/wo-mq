# wo-mq
A simple message queue developed by Netty, SpringBoot,Redis and Protobuf, including wo-mq-server and wo-mq-client parts.

## Techonoligies Choosed

- [x] SpringBoot
- [x] Netty
- [x] Redis
- [x] Protobuf
- [x] More...

## Features

- [x] Asynchronous MQ Sever
- [x] Publish-Subscribe messages based 
- [x] Easy to Use
- [x] Simple
- [x] Rapidly Response
- [x] More...

## Usage

### wo-mq-server

1. The message queue storage used is Redis, please ensure your Redis server is up before run wo-mq-server.

2. Run server using *jar -jar* as below:

   ```shell
   java -jar ./server/build/server-0.0.1-SNAPSHOT.jar [option]
   ```

   *[option]* - there is only one option *--port* provided for you to appoint the server port, default is 8880.

### wo-mq-client

1. Install  all libraies in the directory*./repository/* to your projec trepsoitory.

2. The wo-mq-client provides the following actions for use as below:

   1. publish - create a queue using the given name and put message into the queue.

   2. subscribe - subscribe messages from the given name queue.

   3. put - put a message to the given name queue.

   4. get - get all messages from the subscribed queue by given name.

   5. unsubscribe - unsubscribe messages from the given name queue.

      For example,

   ```java
    WoMqClient mqClient = WoMqClient.getInstance("localhost", 8880);
    mqClient.publish(logName, logInfo); 
    mqClient.subscribe(logName);
    mqClient.put(logName,"心想事成");
    return mqClient.get(logName);
   ```

   ​	a.) Create a wo-mq-client instance by given host and port ;

   ```java
   WoMqClient.getInstance("localhost", 8880);
   ```

   ​	b.) Publish message to the given queue *logName*;

   ```java
   mqClient.publish(logName, logInfo); 
   ```

   ​	c.) Subscribe message from the *logName* queue;

   ```java
   mqClient.subscribe(logName);
   ```

   ​	d.) Put message to the *logName* queue.

   ```java
   mqClient.put(logName,"心想事成");
   ```

   ​	e.) return the message got from the *logName* queue.

   ```
   return mqClient.get(logName);
   ```

   

## Dependencies

### 	wo-mq-server

```xml
<dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <!-- https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-data-redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-all</artifactId>
            <version>4.1.33.Final</version>
        </dependency>

        <dependency>
            <groupId>com.google.protobuf</groupId>
            <artifactId>protobuf-java</artifactId>
            <version>3.6.1</version>
        </dependency>
        <!-- https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.9.8</version>
        </dependency>
        <dependency>
            <groupId>wo.mq</groupId>
            <artifactId>common</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
```



### 	wo-mq-client

```xml
<dependencies>
        <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-all</artifactId>
            <version>4.1.33.Final</version>
        </dependency>

        <dependency>
            <groupId>com.google.protobuf</groupId>
            <artifactId>protobuf-java</artifactId>
            <version>3.6.1</version>
        </dependency>

        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.2.3</version>
        </dependency>
        <dependency>
            <groupId>wo.mq</groupId>
            <artifactId>common</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
```

