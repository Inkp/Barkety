package us.troutwine.barkety

import akka.actor.{Props, AllForOneStrategy, Actor, ActorRef}
import com.typesafe.scalalogging.slf4j.Logger
import org.jivesoftware.smack.chat.{ChatManager, ChatMessageListener, ChatManagerListener, Chat}
import org.jivesoftware.smack.packet.{Presence, Message}
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.tcp.{XMPPTCPConnectionConfiguration, XMPPTCPConnection}
import org.jivesoftware.smackx.muc.{MultiUserChatManager, DiscussionHistory, MultiUserChat}
import org.slf4j.LoggerFactory
import scala.collection.mutable
import org.jivesoftware.smack._

private sealed abstract class InternalMessage
private case class RemoteChatCreated(jid:JID,chat:Chat) extends InternalMessage
private case class ReceivedMessage(msg:String) extends InternalMessage

sealed abstract class Memo
case class CreateChat(jid:JID) extends Memo
case class RegisterParent(ref:ActorRef) extends Memo
case class InboundMessage(msg:String) extends Memo
case class OutboundMessage(msg:String) extends Memo
case class JoinRoom(room: JID, nickname: Option[String] = None, roomPassword: Option[String] = None) extends Memo

object logger {
  val log = Logger(LoggerFactory.getLogger("name")) //TODO: logging
}
import logger._
private class ChatListener(parent:ActorRef) extends ChatManagerListener {
  override def chatCreated(chat:Chat, createdLocally:Boolean) = {
    val jid:JID = JID(chat.getParticipant)
    if (createdLocally)
      log.info("A local chat with %s was created.".format(jid))
    else {
      log.info("%s has begun to chat with us.".format(jid))
      parent ! RemoteChatCreated(jid, chat)
    }
  }
}

private class MsgListener(parent:ActorRef) extends ChatMessageListener {
  override def processMessage(chat: Chat, msg:Message) = {
    if (msg.getBody != null)
      parent ! ReceivedMessage(msg.getBody)
  }
}

private class MsgLogger extends ChatMessageListener {
  override def processMessage(chat: Chat, msg:Message) = {
    log.info("INBOUND %s --> %s : %s".format(chat.getParticipant,
                                                   chat.getThreadID,
                                                   msg.getBody))
  }
}

private class Chatter(chat:Chat, roster:Roster) extends Actor {
  chat.addMessageListener(new MsgListener(self))
  var parent:Option[ActorRef] = None

  def receive = {
    case RegisterParent(ref) =>
      parent = Some(ref)
    case OutboundMessage(msg) =>
      if ( roster.contains(chat.getParticipant) )
        chat.sendMessage(msg)
    case msg:String =>
      chat.sendMessage(msg)
    case msg:ReceivedMessage =>
      parent map { _ ! InboundMessage(msg.msg) }
  }
}

class RoomChatter(muc: MultiUserChat, nickname: String, password: Option[String] = None) extends Actor {
  muc.addMessageListener(new MessageListener() {
    def processMessage(msg: Message) {
      self ! ReceivedMessage(msg.getBody)
    }
  })
  var parent:Option[ActorRef] = None
  
  case object Join
  
  override def preStart() = self ! Join
  
  override def postStop() = muc.leave() // TODO: If receiving Join blows up, will this call blow up too?
  
  def receive = {
    case Join =>
      val history = new DiscussionHistory()
      history.setMaxChars(0) // Don't get anything when joining
      muc.join(nickname, password.getOrElse(null), history, 5000)
    case RegisterParent(ref) =>
      parent = Some(ref)
    case msg: ReceivedMessage =>
      parent map { _ ! InboundMessage(msg.msg) }
    case msg: String => 
      muc.sendMessage(msg)
  }
}

class ChatSupervisor(jid:JID, password:String,
                     domain:Option[String] = None,
                     port:Option[Int] = None) extends Actor
{
  //self.faultHandler = AllForOneStrategy(List(classOf[Throwable]), 5, 5000)
  //self.id = "chatsupervisor:%s".format(jid)

  private val conf = XMPPTCPConnectionConfiguration.builder()
    .setServiceName(domain.getOrElse(jid.domain))
    .setPort(port.getOrElse(5222))
    .setHost(jid.domain).build()
  private val conn = new XMPPTCPConnection(conf)
  conn.connect()
  domain match {
    case Some("talk.google.com") => conn.login(jid, password)
    case _ => conn.login(jid.username, password)
  }
  private val roster:Roster = Roster.getInstanceFor(conn);
  roster.setSubscriptionMode(Roster.SubscriptionMode.accept_all)
  conn.sendPacket( new Presence(Presence.Type.available) )

  private val chats:mutable.Map[JID,Chat] = new mutable.HashMap
  private val msglog:MsgLogger = new MsgLogger

  def receive = {
    case CreateChat(partnerJID) => {
      val chat = ChatManager.getInstanceFor(conn).createChat(partnerJID, msglog)
      if ( !roster.contains(partnerJID) )
        roster.createEntry(partnerJID, partnerJID, null)
      val chatter = context.actorOf(Props(new Chatter(chat, roster)))
      sender ! chatter
    }
    case RemoteChatCreated(partnerJID,chat) =>
      chats.put(partnerJID,chat)
    case JoinRoom(roomId, nickname, password) => 
      val roomChatter = context.actorOf(Props(new RoomChatter(MultiUserChatManager.getInstanceFor(conn).getMultiUserChat(roomId), nickname.getOrElse(jid.username), password)))
      sender ! roomChatter
  }

  override def postStop = {
    conn.disconnect()
  }
}