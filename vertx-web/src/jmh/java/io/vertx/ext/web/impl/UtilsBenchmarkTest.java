package io.vertx.ext.web.impl;

import org.openjdk.jmh.annotations.Benchmark;

public class  UtilsBenchmarkTest {

   private static final String p = "/path";

   @Benchmark
   public void current(){
      Utils.normalisePath(p, false);
   }

   @Benchmark
   public void replacement(){
      Utils.newNormalisePath(p, false);
   }
}
