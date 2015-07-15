/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat.raft.protocol;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import net.kuujo.copycat.Listener;
import net.kuujo.copycat.raft.Member;
import net.kuujo.copycat.util.concurrent.Context;
import net.kuujo.copycat.util.concurrent.CopycatThread;
import net.kuujo.copycat.util.concurrent.CopycatThreadFactory;
import net.kuujo.copycat.util.concurrent.SingleThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty server.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class NettyServer implements Server {
  private static final Logger LOGGER = LoggerFactory.getLogger(NettyServer.class);
  private static final ByteBufAllocator ALLOCATOR = new PooledByteBufAllocator(true);

  private final int id;
  private final Map<Channel, NettyConnection> connections = new ConcurrentHashMap<>();
  private ChannelGroup channelGroup;
  private EventLoopGroup workerGroup;
  private ListenerHolder listener;
  private volatile boolean listening;
  private CompletableFuture<Void> listenFuture;

  public NettyServer(int id) {
    this.id = id;
  }

  @Override
  public int id() {
    return id;
  }

  /**
   * Returns the current execution context.
   */
  private Context getContext() {
    Context context = Context.currentContext();
    if (context == null) {
      throw new IllegalStateException("not on a Copycat thread");
    }
    return context;
  }

  @Override
  public CompletableFuture<Void> listen(Member member, Listener<Connection> listener) {
    if (listening)
      return CompletableFuture.completedFuture(null);

    Context context = getContext();
    synchronized (this) {
      if (listenFuture == null) {
        this.listener = new ListenerHolder(listener, context);
        listenFuture = new CompletableFuture<>();
        listen(member, context);
      }
    }
    return listenFuture;
  }

  /**
   * Starts listening for the given member.
   */
  private void listen(Member member, Context context) {
    channelGroup = new DefaultChannelGroup("copycat-acceptor-channels", GlobalEventExecutor.INSTANCE);
    workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors(), new CopycatThreadFactory("copycat-event-loop-thread-%d"));

    final ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(workerGroup)
      .channel(NioServerSocketChannel.class)
      .handler(new LoggingHandler(LogLevel.DEBUG))
      .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        public void initChannel(SocketChannel channel) throws Exception {
          ChannelPipeline pipeline = channel.pipeline();
          pipeline.addLast(new LengthFieldPrepender(2));
          pipeline.addLast(new LengthFieldBasedFrameDecoder(8192, 0, 2, 0, 2));
          pipeline.addLast(new ServerHandlerAdapter());
        }
      })
      .option(ChannelOption.SO_BACKLOG, 128)
      .option(ChannelOption.TCP_NODELAY, true)
      .option(ChannelOption.SO_REUSEADDR, true)
      .childOption(ChannelOption.ALLOCATOR, ALLOCATOR)
      .childOption(ChannelOption.SO_KEEPALIVE, true);

    try {
      // Bind and start to accept incoming connections.
      InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(member.host()), member.port());

      LOGGER.info("Binding to {}", address);

      ChannelFuture bindFuture = bootstrap.bind(member.host(), member.port());
      bindFuture.addListener((ChannelFutureListener) channelFuture -> {
        channelFuture.channel().closeFuture().addListener(closeFuture -> {
          workerGroup.shutdownGracefully();
        });

        if (channelFuture.isSuccess()) {
          listening = true;
          member.configure(member.host(), ((InetSocketAddress) bindFuture.channel().localAddress()).getPort());
          context.execute(() -> {
            LOGGER.info("Listening at {}:{}", member.host(), member.port());
            listenFuture.complete(null);
          });
        } else {
          context.execute(() -> listenFuture.completeExceptionally(channelFuture.cause()));
        }
      });
      channelGroup.add(bindFuture.channel());
    } catch (Exception e) {
      listenFuture.completeExceptionally(e);
    }
  }

  @Override
  public CompletableFuture<Void> close() {
    return null;
  }

  /**
   * Server handler adapter.
   */
  private class ServerHandlerAdapter extends NettyHandler {

    @Override
    protected NettyConnection getConnection(Channel channel) {
      return connections.get(channel);
    }

    @Override
    protected NettyConnection removeConnection(Channel channel) {
      return connections.remove(channel);
    }

    /**
     * Returns the current execution context or creates one.
     */
    private Context getOrCreateContext(Channel channel) {
      Context context = Context.currentContext();
      if (context != null) {
        return context;
      }

      Thread thread = Thread.currentThread();
      if (!(thread instanceof CopycatThread)) {
        throw new IllegalStateException("illegal thread state");
      }

      context = new SingleThreadContext(channel.eventLoop(), listener.context.serializer().clone());
      ((CopycatThread) thread).setContext(context);
      return context;
    }

    @Override
    public void channelRead(final ChannelHandlerContext context, Object message) {
      ByteBuf buffer = (ByteBuf) message;
      int type = buffer.readByte();
      switch (type) {
        case NettyConnection.CONNECT:
          handleConnect(buffer, context);
          break;
        case NettyConnection.REQUEST:
          handleRequest(buffer, context);
          break;
        case NettyConnection.RESPONSE:
          handleResponse(buffer, context);
          break;
      }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext context) throws Exception {
      context.flush();
    }

    /**
     * Handles a connection identification request.
     */
    private void handleConnect(ByteBuf request, ChannelHandlerContext context) {
      Channel channel = context.channel();
      NettyConnection connection = new NettyConnection(request.readInt(), channel, getOrCreateContext(channel));
      connections.put(channel, connection);
      listener.context.execute(() -> listener.listener.accept(connection));
    }

    /**
     * Handles a request.
     */
    private void handleRequest(ByteBuf request, ChannelHandlerContext context) {
      NettyConnection connection = getConnection(context.channel());
      if (connection != null) {
        connection.handleRequest(request);
      }
    }

    /**
     * Handles a response.
     */
    private void handleResponse(ByteBuf response, ChannelHandlerContext context) {
      NettyConnection connection = getConnection(context.channel());
      if (connection != null) {
        connection.handleResponse(response);
      }
    }
  }

  /**
   * Holds a listener and context.
   */
  private static class ListenerHolder {
    private final Listener<Connection> listener;
    private final Context context;

    private ListenerHolder(Listener<Connection> listener, Context context) {
      this.listener = listener;
      this.context = context;
    }
  }

}
