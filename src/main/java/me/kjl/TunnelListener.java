package me.kjl;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpContinueReadHandler;
import io.undertow.util.Headers;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.jboss.logging.Logger;

public class TunnelListener {
  private static final String CLIP_FILE = "/tmp/ssh-clipboard.txt";

  private static Logger log = Logger.getLogger(TunnelListener.class);

  public static void main(String[] args) throws Exception {
    System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
    System.setProperty("logging.configuration", "logging.properties");
    Undertow server = Undertow.builder()
      .addHttpListener(12346, "127.0.0.1")
      .setHandler(new HttpContinueReadHandler(new HttpHandler() {
        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
          log.info("Received clipboard request");
          ReadableByteChannel c = exchange.getRequestChannel();
          FileChannel file = FileChannel.open(Paths.get(CLIP_FILE), 
                                              StandardOpenOption.WRITE, 
                                              StandardOpenOption.TRUNCATE_EXISTING,
                                              StandardOpenOption.CREATE);
          long position = 0;
          final long count = 4096;
          long transferred = 0;
          try {
            do {
              transferred = file.transferFrom(c, position, count);
            } while(transferred == 4096);
          } catch(Exception e) {
            e.printStackTrace();
          } finally {
            file.close();
          }
          ProcessBuilder pBuilder = new ProcessBuilder("pbcopy");
          pBuilder.redirectInput(new File(CLIP_FILE));
          Process p = pBuilder.start();
          int status = p.waitFor();
          new File(CLIP_FILE).delete();
          if(status != 0) {
            exchange.setResponseCode(500);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("Failed to write to remote clipboard\n");
            log.error("Failure");
          } else {
            log.info("Success");
          }
        }
      })).build();
    server.start();
  }
}
