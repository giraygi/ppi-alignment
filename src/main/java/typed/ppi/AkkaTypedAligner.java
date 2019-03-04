package typed.ppi;



import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import akka.typed.ActorRef;
import akka.typed.ActorSystem;
import akka.typed.Behavior;
import akka.typed.javadsl.Actor;
import akka.typed.javadsl.AskPattern;
import akka.util.Timeout;


/**
 * Hello world!
 *
 */
public class AkkaTypedAligner 
{
	


    public abstract static class HelloWorld {
      //no instances of this class, it's only a name space for messages
      // and static methods
      private HelloWorld() {
      }

      public static final class Greet{
        public final String whom;
        public final ActorRef<Greeted> replyTo;

        public Greet(String whom, ActorRef<Greeted> replyTo) {
          this.whom = whom;
          this.replyTo = replyTo;
        }
      }

      public static final class Greeted {
        public final String whom;

        public Greeted(String whom) {
          this.whom = whom;
        }
      }

      public static final Behavior<Greet> greeter = Actor.immutable((ctx, msg) -> {
        System.out.println("Hello " + msg.whom + "!");
        msg.replyTo.tell(new Greeted(msg.whom));
        return Actor.same();
      });
    }
	
    public static void main( String[] args )
    {
        final ActorSystem<HelloWorld.Greet> system =
          ActorSystem.create(HelloWorld.greeter, "hello");

        final CompletionStage<HelloWorld.Greeted> reply =
          AskPattern.ask(system,(ActorRef<HelloWorld.Greeted> replyTo) -> new HelloWorld.Greet("world", replyTo), new Timeout(3, TimeUnit.SECONDS), system.scheduler());

        reply.thenAccept(greeting -> {
          System.out.println("result: " + greeting.whom);
          system.terminate();
        });
    }
}
