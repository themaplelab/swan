package ca.ualberta.maple.swan.spds.probe;

import java.util.*;
import java.io.*;
import java.net.*;

public class Jui {
    public void run(String[] args) {
        int port = 8088;
        for( int i = 0; i < args.length; i++ ) {
            if( args[i].equals("-port") ) {
                port = Integer.parseInt(args[i+1]);
            }
        }
        try {
            ServerSocket listener = new ServerSocket(port);
            while(true) {
                Socket client = listener.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(
                                client.getInputStream()));
                PrintStream out = new PrintStream(client.getOutputStream(), 
                                        true, "ISO-8859-1");
                String line = in.readLine();
                if( line != null ) {
                    out.println("HTTP/1.0 200 OK\r");
                    out.println( "Content-Type: text/html\r\n" );
                    out.println( process(line) );
                }
                client.shutdownInput();
                client.shutdownOutput();
                client.close();
            }
        } catch( IOException e ) {
            System.out.println( "caught IOException "+e );
        }
    }
    public String process( String input ) {
        System.out.println( input );
        int i = input.indexOf('?');
        Map args = new HashMap();
        if( i >= 0 ) {
            String parms = input.substring(i+1, input.length());
            int j = parms.indexOf(' ');
            if( j >= 0 ) parms = parms.substring(0, j);
            StringTokenizer tokenizer = new StringTokenizer(parms, "&");
            while( tokenizer.hasMoreTokens() ) {
                String token = tokenizer.nextToken();
                int k = token.indexOf('=');
                String key = token.substring(0, k);
                String value = token.substring(k+1, token.length());
                key = URLDecoder.decode(key);
                value = URLDecoder.decode(value);
                args.put(key, value);
            }
        }
        return process( args );
    }
    // Override this method with your implementation.
    public String process( Map args ) {
        return "<html>Hello World</html>";
    }
    public String html( String body ) {
        return "<html>\n"+body+"\n</html>";
    }
    public String link( String key, String value ) {
        return "<a href="+url(key, value)+" style=\"text-decoration: none\">";
    }
    public String url( String key, String value ) {
        return "\"?"+URLEncoder.encode(key)+"="+URLEncoder.encode(value)+"\"";
    }
    public String url( Map m ) {
        StringBuffer sb = new StringBuffer();
        sb.append( "\"?" );
        boolean first = true;
        for( Iterator keyIt = m.keySet().iterator(); keyIt.hasNext(); ) {
            final String key = (String) keyIt.next();
            if( !first ) sb.append( "&" );
            first = false;
            sb.append( URLEncoder.encode(key) );
            sb.append( "=" );
            sb.append( URLEncoder.encode(m.get(key).toString()) );
        }
        sb.append( "\"" );
        return sb.toString();
    }
    public String escape( String s ) {
        StringBuffer sb = new StringBuffer();
        for( int i = 0; i < s.length(); i++ ) {
            char c = s.charAt(i);
            switch( c ) {
                case '<': sb.append( "&lt;" ); break;
                case '>': sb.append( "&gt;" ); break;
                case '\"': sb.append( "&quot;" ); break;
                case '\'': sb.append( "&#039;" ); break;
                case '\\': sb.append( "&#092;" ); break;
                case '&': sb.append( "&amp;" ); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
