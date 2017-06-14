/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.web.impl;

import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 */
public class Utils extends io.vertx.core.impl.Utils {

  private static final Pattern COMMA_SPLITTER = Pattern.compile(" *, *");
  private static final Pattern SEMICOLON_SPLITTER = Pattern.compile(" *; *");
  private static final Pattern EQUAL_SPLITTER = Pattern.compile(" *= *");

  public static String normalisePath(String path) {
//    return normalisePath(path, true);
    return null;
  }

  public static String path(String path, boolean urldecode) {
     return path;
   }
  
  public static String oldnormalisePath(String path, boolean urldecode) {
    if (path == null) {
      return "/";
    }

    if (path.charAt(0) != '/') {
      path = "/" + path;
    }

    try {
      StringBuilder result = new StringBuilder(path.length());

      for (int i = 0; i < path.length(); i++) {
        char c = path.charAt(i);

        if (c == '+') {
          result.append(' ');
        } else if (c == '/') {
          if (i == 0 || result.charAt(result.length() - 1) != '/')
            result.append(c);
        } else if (urldecode && c == '%') {
          i = processEscapeSequence(path, result, i);
        } else if (c == '.') {
          if (i == 0 || result.charAt(result.length() - 1) != '.')
            result.append(c);
          else
            result.deleteCharAt(result.length() - 1);
        } else {
          result.append(c);
        }
      }

      return result.toString();

    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  static class ScannerBuilder{
    boolean scanMode = false;
    boolean normalized = false;
    StringBuilder builder = null;
    char prior = ' ';
    char precedingPrior = ' ';
    String path = null;
    int position = 0; // path sensitive, not the builder
    
    /**
     * Contstructor that will initialize based on the chosen
     * mode.
     * @param mode enable the scan mode to no-op appending
     * @param p path that is being processed, a null cause a NPE
     */
    ScannerBuilder(boolean mode, String p) {
      path = p;
      setScanMode(mode);
    }
    void setScanMode(boolean mode){
      scanMode = mode;
      if (!scanMode){
        builder = new StringBuilder(path.substring(0, position)); //avoid setting the capacity
      }
    }

    void append(char c){
      precedingPrior = prior;
      prior = c;
      if (!scanMode){
        builder.append(c);
      }
    }

    void normalizeAppend(char c, int p){
      if (!scanMode){
        precedingPrior = prior;
        prior = c;
        builder.append(c);
      } else {
        normalized = true;
        position = p;
      }
    }

    char prior(){
      return prior;
    }

    void prePend(char c, int p){
      if (!scanMode){
        precedingPrior = prior;
        prior = c;
        builder.append(c);
      } else {
        normalized = true;
        position = p;
      }
    }

    char charAt(int position){
      return builder.charAt(position);
    }

    boolean isNormalised(){
      return normalized;
    }

    void normaliseSkip(int p){
      normalized = true;
      position = p;
    }

    public String toString(){
      return builder.toString();
    }

    boolean isScanMode(){
      return scanMode;
    }

    int getPosition(){
      return position;
    }
  }

  public static String normalisePath(String path, boolean urldecode) {
     if (path == null) {
        return "/";
      }

    try {
      ScannerBuilder builder = new ScannerBuilder(true, path);
      normalise(builder, path, urldecode);

      if (builder.isNormalised()){
        builder.setScanMode(false);
        normalise(builder, path, urldecode);
        return builder.toString();
      } else
        return path;

    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void normalise(ScannerBuilder builder, String path, boolean urldecode)
      throws UnsupportedEncodingException{
    if (path.charAt(0) != '/') {
      builder.normalizeAppend('/', 0);
    }
    for (int i = builder.getPosition(); i < path.length(); i++) {
      char c = path.charAt(i);
  
      if (c == '+') {
        builder.normalizeAppend(' ', i);
        if (builder.isScanMode())
          return;
      } else if (c == '/') {
        if (i == 0 || builder.prior() != '/')
          builder.append(c);
        else {
          builder.normaliseSkip(i);
          if (builder.isScanMode())
            return;
        }
      } else if (urldecode && c == '%') {
        i = processEscapeSequence(path, builder, i);
        if (builder.isScanMode() && builder.isNormalised())
          return;
      } else if (c == '.') {
        if (i == 0 || builder.prior() != '.'){
          builder.append(c);
        }  else {
          builder.normaliseSkip(i);
          if (builder.isScanMode())
            return;
        }
      } else {
        builder.append(c);
      }
    }
  }

  /**
   * Processes a escape sequence in path
   *
   * @param path
   *          The original path
   * @param result
   *          The result of unescaping the escape sequence (and removing dangerous constructs)
   * @param i
   *          The index of path where the escape sequence begins
   * @return The index of path where the escape sequence ends
   * @throws UnsupportedEncodingException
   *           If the escape sequence does not represent a valid UTF-8 string
   */
  private static int processEscapeSequence(String path, ScannerBuilder result, int i) throws UnsupportedEncodingException {
    Buffer buf = Buffer.buffer(2);
    do {
      if (i >= path.length() - 2) {
        throw new IllegalArgumentException("Invalid position for escape character: " + i);
      }
      int unescaped = Integer.parseInt(path.substring(i + 1, i + 3), 16);
      if (unescaped < 0) {
        throw new IllegalArgumentException("Invalid escape sequence: " + path.substring(i, i + 3));
      }
      buf.appendByte((byte) unescaped);
      i += 3;
    } while (i < path.length() && path.charAt(i) == '%');

    String escapedSeq = new String(buf.getBytes(), StandardCharsets.UTF_8);

    for (int j = 0; j < escapedSeq.length(); j++) {
      char c = escapedSeq.charAt(j);
      if (c == '/') {
        if (j == 0 || result.prior() != '/')
          result.append(c);
        else
          result.normaliseSkip(i);
      } else if (c == '.') {
        if (j == 0 || result.prior != '.')
          result.append(c);
        else
          result.normaliseSkip(i);
      } else {
        result.append(c);
      }
    }
    return i - 1;
  }

  private static int processEscapeSequence(String path, StringBuilder result, int i) throws UnsupportedEncodingException {
    Buffer buf = Buffer.buffer(2);
    do {
      if (i >= path.length() - 2) {
        throw new IllegalArgumentException("Invalid position for escape character: " + i);
      }
      int unescaped = Integer.parseInt(path.substring(i + 1, i + 3), 16);
      if (unescaped < 0) {
        throw new IllegalArgumentException("Invalid escape sequence: " + path.substring(i, i + 3));
      }
      buf.appendByte((byte) unescaped);
      i += 3;
    } while (i < path.length() && path.charAt(i) == '%');

    String escapedSeq = new String(buf.getBytes(), StandardCharsets.UTF_8);

    for (int j = 0; j < escapedSeq.length(); j++) {
      char c = escapedSeq.charAt(j);
      if (c == '/') {
        if (j == 0 || result.charAt(result.length() - 1) != '/')
          result.append(c);
      } else if (c == '.') {
        if (j == 0 || result.charAt(result.length() - 1) != '.')
          result.append(c);
        else
          result.deleteCharAt(result.length() - 1);
      } else {
        result.append(c);
      }
    }
    return i - 1;
  }

  public static ClassLoader getClassLoader() {
    ClassLoader tccl = Thread.currentThread().getContextClassLoader();
    return tccl == null ? Utils.class.getClassLoader() : tccl;
  }

  public static Buffer readResourceToBuffer(String resource) {
    ClassLoader cl = getClassLoader();
    try {
      Buffer buffer = Buffer.buffer();
      try (InputStream in = cl.getResourceAsStream(resource)) {
        if (in == null) {
          return null;
        }
        int read;
        byte[] data = new byte[4096];
        while ((read = in.read(data, 0, data.length)) != -1) {
          if (read == data.length) {
            buffer.appendBytes(data);
          } else {
            byte[] slice = new byte[read];
            System.arraycopy(data, 0, slice, 0, slice.length);
            buffer.appendBytes(slice);
          }
        }
      }
      return buffer;
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  /*
  Reads from file or classpath
   */
  public static String readFileToString(Vertx vertx, String resource) {
    try {
      Buffer buff = vertx.fileSystem().readFileBlocking(resource);
      return buff.toString();
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  public static List<String> getSortedAcceptableMimeTypes(String acceptHeader) {
    // accept anything when accept is not present
    if (acceptHeader == null) {
      return Collections.emptyList();
    }

    // parse
    String[] items = COMMA_SPLITTER.split(acceptHeader);
    // sort on quality
    Arrays.sort(items, ACCEPT_X_COMPARATOR);

    List<String> list = new ArrayList<>(items.length);

    for (String item : items) {
      // find any ; e.g.: "application/json;q=0.8"
      int space = item.indexOf(';');

      if (space != -1) {
        list.add(item.substring(0, space));
      } else {
        list.add(item);
      }
    }

    return list;
  }

  public static DateFormat createRFC1123DateTimeFormatter() {
    DateFormat dtf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
    dtf.setTimeZone(TimeZone.getTimeZone("GMT"));
    return dtf;
  }

  public static String pathOffset(String path, RoutingContext context) {
    int prefixLen = 0;
    String mountPoint = context.mountPoint();
    if (mountPoint != null) {
      prefixLen = mountPoint.length();
    }
    String routePath = context.currentRoute().getPath();
    if (routePath != null) {
      prefixLen += routePath.length() - 1;
    }
    return prefixLen != 0 ? path.substring(prefixLen) : path;
  }

  private static final Comparator<String> ACCEPT_X_COMPARATOR = new Comparator<String>() {
    float getQuality(String s) {
      if (s == null) {
        return 0;
      }

      String[] params = SEMICOLON_SPLITTER.split(s);
      for (int i = 1; i < params.length; i++) {
        String[] q = EQUAL_SPLITTER.split(params[1]);
        if ("q".equals(q[0])) {
          return Float.parseFloat(q[1]);
        }
      }
      return 1;
    }

    @Override
    public int compare(String o1, String o2) {
      float f1 = getQuality(o1);
      float f2 = getQuality(o2);
      if (f1 < f2) {
        return 1;
      }
      if (f1 > f2) {
        return -1;
      }
      return 0;
    }
  };

  public static long secondsFactor(long millis) {
    return millis - (millis % 1000);
  }
}
