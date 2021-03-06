package info.bitrich.xchangestream.gdax;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.gdax.dto.GDAXWebSocketSubscriptionMessage;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import info.bitrich.xchangestream.gdax.netty.WebSocketClientCompressionAllowClientNoContextHandler;
import info.bitrich.xchangestream.service.netty.WebSocketClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static info.bitrich.xchangestream.gdax.dto.GDAXWebSocketSubscriptionMessage.PRODUCT_ID;

public class GDAXStreamingService extends JsonNettyStreamingService {
  private static final Logger LOG = LoggerFactory.getLogger(GDAXStreamingService.class);
  private static final String SUBSCRIBE = "subscribe";
  private static final String UNSUBSCRIBE = "unsubscribe";
  private final Map<String, Observable<JsonNode>> subscriptions = new HashMap<>();
  
  private WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler = null;

  public GDAXStreamingService(String apiUrl) {
    super(apiUrl);
  }

  /**
   * Subscribes to the provided channel name, maintains a cache of subscriptions, in order not to 
   * subscribe more than once to the same channel.
   * 
   * @param channelName the name of the requested channel.
   * @return an Observable of json objects coming from the exchange.
   */
  @Override
  public Observable<JsonNode> subscribeChannel(String channelName) {
    if (!channels.containsKey(channelName) && !subscriptions.containsKey(channelName)){
      subscriptions.put(channelName, super.subscribeChannel(channelName));
    } 
    
    return subscriptions.get(channelName);
  }
  
  @Override
  protected String getChannelNameFromMessage(JsonNode message) throws IOException {
    return message.get(PRODUCT_ID).asText();
  }

  @Override
  public String getSubscribeMessage(String channelName) throws IOException {
    GDAXWebSocketSubscriptionMessage subscribeMessage = 
      new GDAXWebSocketSubscriptionMessage(SUBSCRIBE, channelName);
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.writeValueAsString(subscribeMessage);
  }

  @Override
  public String getUnsubscribeMessage(String channelName) throws IOException {
    GDAXWebSocketSubscriptionMessage subscribeMessage =
      new GDAXWebSocketSubscriptionMessage(UNSUBSCRIBE, channelName);
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.writeValueAsString(subscribeMessage);
  }

  @Override
  protected void handleMessage(JsonNode message) {
    super.handleMessage(message);
  }

  @Override
  protected WebSocketClientExtensionHandler getWebSocketClientExtensionHandler() {
    return WebSocketClientCompressionAllowClientNoContextHandler.INSTANCE;
  }
  
  @Override
  protected WebSocketClientHandler getWebSocketClientHandler(WebSocketClientHandshaker handshaker, 
                                                             WebSocketClientHandler.WebSocketMessageHandler handler) {
    LOG.info("Registering GDAXWebSocketClientHandler");
    return new GDAXWebSocketClientHandler(handshaker, handler);
  }
  
  public void setChannelInactiveHandler(WebSocketClientHandler.WebSocketMessageHandler channelInactiveHandler) {
    this.channelInactiveHandler = channelInactiveHandler;
  }

  /**
   * Custom client handler in order to execute an external, user-provided handler on channel events.
   * This is useful because it seems GDAX unexpectedly closes the web socket connection.
   */
  class GDAXWebSocketClientHandler extends  WebSocketClientHandler{

    public GDAXWebSocketClientHandler(WebSocketClientHandshaker handshaker, WebSocketMessageHandler handler) {
      super(handshaker, handler);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      super.channelInactive(ctx);
      if (channelInactiveHandler != null){
        channelInactiveHandler.onMessage("WebSocket Client disconnected!");
      }
    }
  }
}
