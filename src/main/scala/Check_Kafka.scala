//
//  Author: Hari Sekhon
//  Date: 2016-06-06 22:43:57 +0100 (Mon, 06 Jun 2016)
//
//  vim:ts=2:sts=2:sw=2:et
//
//  https://github.com/harisekhon/nagios-plugin-kafka
//
//  License: see accompanying Hari Sekhon LICENSE file
//
//  If you're using my code you're welcome to connect with me on LinkedIn and optionally send me feedback to help steer this or other code I publish
//
//  https://www.linkedin.com/in/harisekhon
//

package com.linkedin.harisekhon

//import com.google.common.io.Resources

//import com.linkedin.harisekhon.Utils._

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

import scala.util.Random
//import java.io.InputStream
import java.util.Properties
import java.util.Arrays
import org.apache.log4j.Level
import org.apache.log4j.Logger
import collection.JavaConversions._
import java.text.SimpleDateFormat

object CheckKafka extends App {
    val check_kafka = new CheckKafka(
                                     broker_list = "192.168.99.100:9092",
                                     topic = "nagios-plugin-kafka-test"
                                     )
//    check_kafka.subscribe()
//    check_kafka.produce()
//    check_kafka.consume()
}

class CheckKafka(
        val broker_list: String = "localhost:9092",
        val topic: String = "test",
        val partition: Int = 0,
        // change default to -1 to ensure all ISRs have written msg
        val required_acks: String = "-1"
                ){

    val log = Logger.getLogger("CheckKafka")

    val uuid = java.util.UUID.randomUUID.toString
    val epoch = System.currentTimeMillis()
    // comes out the same whether specifying single, double or triple data digits
    val date = new SimpleDateFormat("yyyy-dd-MM HH:MM:ss.SSS Z").format(epoch)
    val id: String = s"Hari Sekhon check_kafka (scala) - random token=$uuid, $date"

     // set in log4j.properties
//    log.setLevel(Level.DEBUG)

    // TODO: split consumer + producer values
    val props = new Properties
//    props.put("metadata.broker.list", broker_list)
    props.put("bootstrap.servers", broker_list)
    props.put("client.id", "CheckKafka")
    props put("request.required.acks", required_acks)

    // trying to fail fast
//    props.put("timeout.ms", "5000") // 5 secs for ISR acks
//    props.put("metadata.fetch.timeout.ms", "1000") // 1 sec for metadata on topic connect
//    props.put("consumer.timeout.ms", "1000") // msg must be available within this window
//    props.put("socket.timeout.ms", "1000")
//    props.put("request.timeout.ms", "1000")
//    props.put("reconnect.backoff.ms", "0")
//    props.put("retry.backoff.ms", "0")
//    props.put("session.timeout.ms", "900")
//    props.put("fetch.max.wait.ms", "900")
//    props.put("heartbeat.interval.ms", "100")

    props.put("key.serializer",   "org.apache.kafka.common.serialization.StringSerializer")
    props.put("key.deserializer",   "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")

    // enforce random group id to make sure we get our msg back
//    if(props.getProperty("group.id") == null) {
//        log.debug(s"group.id not set, creating random group id")
//    }
    val group_id: String = s"$uuid, $date"
    props.setProperty("group.id", group_id)
    log.debug(s"group id='$group_id'")

    log.debug("creating Kafka consumer")
    // XXX: TODO: try switch to SimpleConsumer to be able to get rejected properly, also better to be backwards compatible with 0.8 systems
    val consumer = new KafkaConsumer[String, String](props)
//    var consumer: KafkaConsumer[String, String] //= new KafkaConsumer[String, String](props)
    log.debug("creating Kafka producer")
//    val producer: KafkaProducer[String, String] = new KafkaProducer[String, String](props)
    val producer = new KafkaProducer[String, String](props)

    val msg = s"test message generated by $id"
    log.debug(s"test message => '$msg'")

    var latest_offset: Long = 0

    run()

    def run(): Unit = {
        val start = System.currentTimeMillis()
        subscribe(topic)
        val start_write = System.currentTimeMillis()
        produce(topic, msg)
        // if clock gets reset and this become negative I'm not handling it as that should be a super rare one time occurrence
        // unless perhaps there are a lot of NTPd time steps to bring time back inline, but anyway that shouldn't be
        // a regular occurrence that affects this program
        val write_time = (System.currentTimeMillis() - start_write) / 1000.0
        val start_read = System.currentTimeMillis()
        consume(topic)
        val read_time = (System.currentTimeMillis() - start_read) / 1000.0
        val total_time = (System.currentTimeMillis() - start) / 1000.0
        println(s"OK: Kafka broker successfully returned unique message, write_time=${write_time}s, read_time=${read_time}s, total_time=${total_time}s | write_time=${write_time}s, read_time=${read_time}s, total_time=${total_time}s")
    }

    def subscribe(topic: String = topic): Unit = {
        // conflicts with partition assignment
//        log.debug(s"subscribing to topic $topic")
//        consumer.subscribe(Arrays.asList(topic))
        val topic_partition = new TopicPartition(topic, partition)
        log.debug(s"assigning partition $partition")
        consumer.assign(Arrays.asList(topic_partition))
//        consumer.assign(Arrays.asList(partition))
        // not connected to port so no conn refused at this point
        // loops from here indefinitely if connection refused
        latest_offset = consumer.position(topic_partition)
    }

    def produce(topic: String = topic, msg: String = msg): Unit = {
        //        InputStream props = Resources.getResource("file.properties").openStream()
//        try{
        log.debug("sending message")
        producer.send(new ProducerRecord[String, String](topic, msg)) // key and partition optional
        log.debug("flushing")
        producer.flush()
        log.debug("closing producer")
        producer.close() // blocks until msgs are sent
//        } catch(Throwable t){
//            println("%s", t.getStackTrace)
//        }
//        finally {
//            producer.close() // blocks until msgs are sent
//        }
    }

    def consume(topic: String = topic): Unit = {
        log.debug("consuming")
        val records: ConsumerRecords[String, String] = consumer.poll(200) // ms
        log.debug("closing consumer")
        consumer.close()
        val consumed_record_count: Int = records.count()
        log.debug(s"consumed record count = $consumed_record_count")
        assert(consumed_record_count != 0)
        var msg2: String = null
        for(record: ConsumerRecord[String, String] <- records){
            val record_topic = record.topic()
            log.debug(s"found message with topic $record_topic")
            assert(topic.equals(record_topic))
            if(msg.equals(record.value())){
                msg2 = record.value()
            }
        }
        log.debug(s"message returned: $msg2")
        log.debug(s"message expected: $msg")
        assert(msg.equals(msg2))
    }

}