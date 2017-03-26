# Overview
Java library for creating "LINE BOT" as HttpServlet.

It is licensed under [MIT](https://opensource.org/licenses/MIT).

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.riversun/line-bot-helper/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.riversun/line-bot-helper)

# Example for HttpServlet

```java
@SuppressWarnings("serial")
public class LineBotExample01Servlet extends LineBotServlet {

	private static final String CHANNEL_SECRET ="[YOUR_CHANNEL_SECRET_HERE]" ;
	private static final String CHANNEL_ACCESS_TOKEN ="[YOUR_CHANNEL_ACCESS_TOKEN_HERE]";
	@Override
	protected ReplyMessage handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws IOException {

		TextMessageContent userMessage = event.getMessage();

		// Get user profile
		UserProfileResponse userProfile = getUserProfile(event.getSource().getUserId());


		String botResponseText = "Hi,"+userProfile.getDisplayName() + ","
				+ "You say '" + userMessage.getText() + "' !";

		TextMessage textMessage = new TextMessage(botResponseText);

		return new ReplyMessage(event.getReplyToken(), Arrays.asList(textMessage));
	}

	@Override
	protected ReplyMessage handleDefaultMessageEvent(Event event) {
		//When other messages not overridden as handle* is received, do nothing (returns null)
		return null;
	}

	@Override
	public String getChannelSecret() {
		return CHANNEL_SECRET;
	}

	@Override
	public String getChannelAccessToken() {
		return CHANNEL_ACCESS_TOKEN;
	}
```
