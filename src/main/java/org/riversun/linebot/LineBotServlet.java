/*  LineBotServlet for line-bot-sdk-java
 *
 *  Copyright (c) 2017 Tom Misawa, riversun.org@gmail.com
 *  
 *  Permission is hereby granted, free of charge, to any person obtaining a
 *  copy of this software and associated documentation files (the "Software"),
 *  to deal in the Software without restriction, including without limitation
 *  the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *  and/or sell copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following conditions:
 *  
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 *  DEALINGS IN THE SOFTWARE.
 *  
 */
package org.riversun.linebot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import retrofit2.Response;

import com.google.common.io.ByteStreams;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.LineMessagingClientImpl;
import com.linecorp.bot.client.LineMessagingServiceBuilder;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.BeaconEvent;
import com.linecorp.bot.model.event.CallbackRequest;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.FollowEvent;
import com.linecorp.bot.model.event.JoinEvent;
import com.linecorp.bot.model.event.LeaveEvent;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.UnfollowEvent;
import com.linecorp.bot.model.event.message.AudioMessageContent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.LocationMessageContent;
import com.linecorp.bot.model.event.message.MessageContent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.message.VideoMessageContent;
import com.linecorp.bot.model.profile.UserProfileResponse;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.servlet.LineBotCallbackException;
import com.linecorp.bot.servlet.LineBotCallbackRequestParser;

/**
 * Base Servlet for line-bot-sdk-java(https://github.com/line/line-bot-sdk-java)
 * 
 * @author Tom Misawa (riversun.org@gmail.com)
 *
 */
public abstract class LineBotServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Logger.getLogger(LineBotServlet.class.getName());

	/**
	 * Returns CHANNEL SECRET
	 * 
	 * @return
	 */
	public abstract String getChannelSecret();

	/**
	 * Return ChannelAccessToken
	 * 
	 * @return
	 */
	public abstract String getChannelAccessToken();

	/**
	 * httpServletRequest(Implicit Object)
	 */
	protected HttpServletRequest req;

	/**
	 * httpServletResponse(Implicit Object)
	 */
	protected HttpServletResponse res;

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		this.req = req;
		this.res = res;
		super.service(req, res);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {

		LOGGER.fine("callback from LINE received.");

		final LineSignatureValidator validator = new LineSignatureValidator(getChannelSecret().getBytes(StandardCharsets.UTF_8));
		final LineBotCallbackRequestParser parser = new LineBotCallbackRequestParser(validator);

		final String signature = req.getHeader("X-Line-Signature");

		final byte[] jsonData = ByteStreams.toByteArray(req.getInputStream());

		final String jsonText = new String(jsonData, StandardCharsets.UTF_8);

		// if log level set finest ,show json response.
		if (LOGGER.isLoggable(Level.FINEST)) {

			final JSONObject jsonObj = new JSONObject(jsonText);

			// pretty printing
			LOGGER.finest("RESPONSE JSON\n" + jsonObj.toString(4));
		}

		CallbackRequest callbackRequest;

		try {
			callbackRequest = parser.handle(signature, jsonText);

			final List<Event> eventList = callbackRequest.getEvents();

			for (int i = 0; i < eventList.size(); i++) {

				final Event event = eventList.get(i);

				LOGGER.fine("event[" + i + "]=" + event);

				if (event == null) {
					continue;
				}

				if (event instanceof MessageEvent) {
					final MessageEvent<?> messageEvent = (MessageEvent<?>) event;
					reply(handleMessageEvent(messageEvent));
				}
				else if (event instanceof UnfollowEvent) {
					UnfollowEvent unfollowEvent = (UnfollowEvent) event;
					handleUnfollowEvent(unfollowEvent);
				}
				else if (event instanceof FollowEvent) {
					FollowEvent followEvent = (FollowEvent) event;
					reply(handleFollowEvent(followEvent));
				}
				else if (event instanceof JoinEvent) {
					final JoinEvent joinEvent = (JoinEvent) event;
					reply(handleJoinEvent(joinEvent));
				}
				else if (event instanceof LeaveEvent) {
					final LeaveEvent leaveEvent = (LeaveEvent) event;
					handleLeaveEvent(leaveEvent);
				}
				else if (event instanceof PostbackEvent) {
					final PostbackEvent postbackEvent = (PostbackEvent) event;
					reply(handlePostbackEvent(postbackEvent));
				}
				else if (event instanceof BeaconEvent) {
					final BeaconEvent beaconEvent = (BeaconEvent) event;
					reply(handleBeaconEvent(beaconEvent));
				}

			}

		} catch (LineBotCallbackException e) {
			e.printStackTrace();
		}
		res.setStatus(200);
	}

	@SuppressWarnings("unchecked")
	private ReplyMessage handleMessageEvent(MessageEvent<?> messageEvent) throws IOException {

		final MessageContent messageContent = messageEvent.getMessage();
		if (messageContent == null) {
			return null;
		}

		LOGGER.fine("message content=" + messageContent);

		if (messageContent instanceof TextMessageContent) {
			final MessageEvent<TextMessageContent> event = (MessageEvent<TextMessageContent>) messageEvent;
			return handleTextMessageEvent(event);
		}
		else if (messageContent instanceof ImageMessageContent) {
			final MessageEvent<ImageMessageContent> event = (MessageEvent<ImageMessageContent>) messageEvent;
			return handleImageMessageEvent(event);
		}
		else if (messageContent instanceof LocationMessageContent) {
			final MessageEvent<LocationMessageContent> event = (MessageEvent<LocationMessageContent>) messageEvent;
			return handleLocationMessageEvent(event);
		}
		else if (messageContent instanceof AudioMessageContent) {
			final MessageEvent<AudioMessageContent> event = (MessageEvent<AudioMessageContent>) messageEvent;
			return handleAudioMessageEvent(event);
		}
		else if (messageContent instanceof VideoMessageContent) {
			final MessageEvent<VideoMessageContent> event = (MessageEvent<VideoMessageContent>) messageEvent;
			return handleVideoMessageEvent(event);
		}
		else if (messageContent instanceof StickerMessageContent) {
			final MessageEvent<StickerMessageContent> event = (MessageEvent<StickerMessageContent>) messageEvent;
			return handleStickerMessageEvent(event);
		} else {
			return null;
		}

	}

	/**
	 * Called when a TextMessage is received
	 * 
	 * @param event
	 * @return
	 * @throws IOException
	 */
	protected ReplyMessage handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws IOException {
		LOGGER.fine("do handle TextMessageEvent ");
		return handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a ImageMessage is received
	 * 
	 * @param event
	 * @return
	 * @throws IOException
	 */
	protected ReplyMessage handleImageMessageEvent(MessageEvent<ImageMessageContent> event) throws IOException {
		LOGGER.fine("do handle ImageMessageEvent");
		return handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a LocationMessage is received
	 * 
	 * @param event
	 * @return
	 * @throws IOException
	 */
	protected ReplyMessage handleLocationMessageEvent(MessageEvent<LocationMessageContent> event) {
		LOGGER.fine("do handle LocationMessageEvent");
		return handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a StickerMessage is received
	 * 
	 * @param event
	 * @return
	 */
	protected ReplyMessage handleStickerMessageEvent(MessageEvent<StickerMessageContent> event) {
		LOGGER.fine("do handle StickerMessageEvent");
		return handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a AudioMessage is received
	 * 
	 * @param event
	 * @return
	 * @throws IOException
	 */
	protected ReplyMessage handleAudioMessageEvent(MessageEvent<AudioMessageContent> event) throws IOException {
		LOGGER.fine("do handle AudioMessageEvent");
		return handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a VideoMessage is received
	 * 
	 * @param event
	 * @return
	 * @throws IOException
	 */
	protected ReplyMessage handleVideoMessageEvent(MessageEvent<VideoMessageContent> event) throws IOException {
		LOGGER.fine("do handle VideoMessageEvent");
		return handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a UnfollowEvent is received
	 * 
	 * @param event
	 */
	protected void handleUnfollowEvent(UnfollowEvent event) {
		LOGGER.fine("do handle UnfollowEvent");
		handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a FollowEvent is received
	 * 
	 * @param event
	 * @return
	 */
	protected ReplyMessage handleFollowEvent(FollowEvent event) {
		LOGGER.fine("do handle FollowEvent");
		return handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a JoinEvent is received
	 * 
	 * @param event
	 * @return
	 */
	protected ReplyMessage handleJoinEvent(JoinEvent event) {
		LOGGER.fine("do handle JoinEvent");
		return handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a LeaveEvent is received
	 * 
	 * @param event
	 */
	protected void handleLeaveEvent(LeaveEvent event) {
		LOGGER.fine("do handle LeaveEvent");
		handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a PostbackEvent is received
	 * 
	 * @param event
	 * @return
	 */
	protected ReplyMessage handlePostbackEvent(PostbackEvent event) {
		LOGGER.fine("do handle PostbackEvent");
		return handleDefaultMessageEvent(event);
	}

	/**
	 * Called when a BeaconEvent is received
	 * 
	 * @param event
	 * @return
	 */
	protected ReplyMessage handleBeaconEvent(BeaconEvent event) {
		LOGGER.fine("do handle BeaconEvnt");
		return handleDefaultMessageEvent(event);

	}

	/**
	 * 
	 * When other messages not overridden as handle* is received.
	 * 
	 * @param event
	 * @return
	 */
	protected abstract ReplyMessage handleDefaultMessageEvent(Event event);

	private void reply(ReplyMessage replyMessage) throws IOException {
		LOGGER.fine("send reply replyMessage=" + replyMessage);
		if (replyMessage == null) {
			return;
		}

		Response<BotApiResponse> response = LineMessagingServiceBuilder
				.create(getChannelAccessToken())
				.build()
				.replyMessage(replyMessage)
				.execute();

		// show network level message
		LOGGER.fine("send reply response=" + response.raw().toString());

	}

	/**
	 * Get User Profile
	 * 
	 * @param userId
	 * @return
	 */
	public final UserProfileResponse getUserProfile(String userId) {

		final LineMessagingClient lineMessagingClient = new LineMessagingClientImpl(
				LineMessagingServiceBuilder
						.create(getChannelAccessToken())
						.build()
				);
		try {
			UserProfileResponse userProfileResponse = lineMessagingClient.getProfile(userId).get();
			return userProfileResponse;

		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}

	}

	/**
	 * Get InputStream of contents such as images
	 * 
	 * @param content
	 * @return
	 */
	public final InputStream getContentStream(MessageContent content) {

		String messageId = content.getId();

		final LineMessagingClient lineMessagingClient = new LineMessagingClientImpl(
				LineMessagingServiceBuilder
						.create(getChannelAccessToken())
						.build()
				);

		try {
			MessageContentResponse res;
			res = lineMessagingClient.getMessageContent(messageId).get();

			final InputStream contentStream = res.getStream();
			return contentStream;

		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}

	}
}