package io.vertx.ext.web.impl;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class  UtilsBenchmarkTest {

   private static final String p = "/path";

   @Benchmark
   public void current(){
      Blackhole.consumeCPU( (long)Utils.normalisePath(p, false).charAt(0) );
   }

   @Benchmark
   public void replacement(){
      Blackhole.consumeCPU( (long)Utils.newNormalisePath(p, false).charAt(0) );
   }
   
   @Benchmark
   public void baseline(){
      Blackhole.consumeCPU( (long)Utils.path(p, false).charAt(0) );
   }
   
}
