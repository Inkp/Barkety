package us.troutwine.barkety

import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import org.scalatest._
import akka.testkit.{TestKitBase, TestKit}
import akka.actor.{ActorSystem, ActorRef, Props}
import akka.pattern.ask
import org.scalatest.mock.MockitoSugar


class BarketySpec extends FlatSpec with Matchers with TestKitBase with MockitoSugar {
  implicit lazy val system = ActorSystem()
  implicit val timeout: Timeout = 5.seconds
  "The Chat supervisor" should "boot with no problems" in {
      val jid = JID("barketyTest@jabber.org")
      val chatsupRef = system.actorOf(Props(new ChatSupervisor(jid, "123456")))
      system.stop(chatsupRef)
    }

    it should "create a chatter on request" in {
      val jid = JID("barketyTest@jabber.org")
      val me  = JID("troutwine@jabber.org")
      val chatsup = system.actorOf(Props(new ChatSupervisor(jid, "123456")))
      chatsup ? CreateChat(me) onSuccess { case None => fail() }
      system.stop(chatsup)
    }

    it should "send me a nice message" in {
      val jid = JID("barketyTest@jabber.org")
      val me  = JID("troutwine@jabber.org")
      val chatsup = system.actorOf(Props(new ChatSupervisor(jid, "123456")))
      chatsup ? CreateChat(me) onSuccess {
        case chatter:ActorRef =>
          chatter ! OutboundMessage("Hi, you!")
        case _ => fail()
      }
      Thread.sleep(1000)
      system.stop(chatsup)
    }

    it should "be able to connect to a Google domain" in {
      val fakey = JID("text1@fluentsms.com")
      val chatsup = system.actorOf(
        Props(new ChatSupervisor(fakey, "Text1234", Some("talk.google.com")))
      )
      expectNoMsg(5 seconds)
      system.stop(chatsup)
    }

  "The JID extractor" should "extract the full-JID components" in {
      val jid = JID("troutwine@jabber.org/helpful")
      jid.username should be === "troutwine"
      jid.domain should be === "jabber.org"
      jid.resource should be === Some("helpful")
    }
    
    it should "handle certain non-word characters in JIDs" in {
      JID("troutwine-foo@jabber.org/wassup")
      JID("troutwine_foo@jabber.org/wassup")
      JID("troutwine.foo@jabber.org/wassup")
    }

    it should "extract partial JID components" in {
      val jid = JID("troutwine@jabber.org")
      jid.username should be === "troutwine"
      jid.domain should be === "jabber.org"
      jid.resource should be === None
    }

    it should "convert to implict string" in {
      val jid:String = JID("troutwine@jabber.org/helpful")
      jid should be === "troutwine@jabber.org/helpful"
    }

    it should "reject nonsense" in {
      intercept[RuntimeException] {
        JID("hihowareyou?")
      }
    }

    it should "provide sane equality" in {
      val jid0:JID = JID("troutwine@jabber.org/")
      val jid1:JID = JID("troutwine@jabber.org/")
      jid1 should be === jid0
      val jid2:JID = JID("troutwine@jabber.org/helpful")
      jid1 should not { be === jid2 }
      val jid3:JID = JID("morbo@earth.org")
      jid1 should not { be === jid3 }
      jid0 match {
        case `jid1` =>
        case _ => fail
      }
    }

    it should "enforce an arbitrary ordering" in {
      val jid0:JID = JID("troutwine@jabber.org")
      val jid1:JID = JID("barkety@jabber.org")
      val jid2:JID = JID("oliver@jabber.org")
      jid1 should be >= jid0
      jid2 should be >= jid0
      jid1 should be >= jid2
    }
}
