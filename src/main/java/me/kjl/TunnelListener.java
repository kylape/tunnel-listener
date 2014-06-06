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
  private static final int DEFAULT_PORT = 12346;
  private static final String DEFAULT_HOST = "localhost";
  private static final int CHUNK_SIZE = 4096;
  private static final String CLIPBOARD_COMMAND = "pbcopy";

  private static Logger log = Logger.getLogger(TunnelListener.class);
  private int port = DEFAULT_PORT;
  private String host = DEFAULT_HOST;

  public static void main(String[] args) throws Exception {
    System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
    System.setProperty("logging.configuration", "logging.properties");
    int port = 0;
    String host = "";
    for(int i=0; i < args.length; i++) {
      String arg = args[i];
      if(arg.equals("-p") || arg.equals("--port")) {
        String val = args[++i];
        try {
          port = Integer.parseInt(val);
        } catch(NumberFormatException e) {
          log.warn("Invalid port number: " + val);
        }
      } else if(arg.equals("-h") || arg.equals("--host")) {
        host = args[++i];
      }
    }
    new TunnelListener(port, host).start();
  }

  public TunnelListener(int port, String host) {
    if(port != 0) {
      this.port = port;
    }
    if(host != "") {
      this.host = host;
    }
  }

  public void start() {
    Undertow server = Undertow.builder()
      .addHttpListener(port, host)
      .setHandler(new HttpContinueReadHandler(new HttpHandler() {
        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
          log.debug("Received clipboard request");
          ReadableByteChannel c = exchange.getRequestChannel();
          FileChannel file = FileChannel.open(Paths.get(CLIP_FILE), 
                                              StandardOpenOption.WRITE, 
                                              StandardOpenOption.TRUNCATE_EXISTING,
                                              StandardOpenOption.CREATE);
          long position = 0;
          final long count = CHUNK_SIZE;
          long transferred = 0;
          try {
            do {
              transferred = file.transferFrom(c, position, count);
            } while(transferred == CHUNK_SIZE);
          } catch(Exception e) {
            e.printStackTrace();
          } finally {
            file.close();
          }
          ProcessBuilder pBuilder = new ProcessBuilder(CLIPBOARD_COMMAND);
          pBuilder.redirectInput(new File(CLIP_FILE));
          Process p = pBuilder.start();
          int status = p.waitFor();
          new File(CLIP_FILE).delete();
          if(status != 0) {
            exchange.setResponseCode(500);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("Failed to write to remote clipboard\n");
            log.error("Failure: Clipboard command failed");
          } else {
            log.debug("Success");
          }
        }
      })).build();
    server.start();
  }
}
