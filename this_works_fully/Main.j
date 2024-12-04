.class public Main
.super java/lang/Object

.field public static result LComplex;

.method public <init>()V
   aload_0
   invokespecial java/lang/Object/<init>()V
   return
.end method

.method public static AddComplex(LComplex;LComplex)LComplex;
.limit stack 10
.limit locals 10
   new Complex
   dup
   invokespecial Complex/<init>()V
   putstatic Main/result LComplex;

   getstatic Main/result LComplex;
   putfield Complex/realPart D

   getstatic Main/result LComplex;
   putfield Complex/imag D

   areturn
.end method

.method public static main([Ljava/lang/String;)V
   new Complex
   dup
   invokespecial Complex/<init>()V
   putstatic Main/c1 LComplex;

   getstatic Main/c1 LComplex;
   ldc2_w 2.500000000000000
   putfield Complex/realPart D

   getstatic Main/c1 LComplex;
   ldc2_w 3.500000000000000
   putfield Complex/imag D

   new Complex
   dup
   invokespecial Complex/<init>()V
   putstatic Main/c2 LComplex;

   getstatic Main/c2 LComplex;
   ldc2_w 1.500000000000000
   putfield Complex/realPart D

   getstatic Main/c2 LComplex;
   ldc2_w 2.500000000000000
   putfield Complex/imag D

   invokestatic Main/AddComplex(LComplex;LComplex)LComplex;
   areturn
   getstatic java/lang/System/out Ljava/io/PrintStream;
   getstatic Main/result LComplex;
   getfield Complex/realPart D
   invokevirtual java/io/PrintStream/println(D)V

   getstatic java/lang/System/out Ljava/io/PrintStream;
   getstatic Main/result LComplex;
   getfield Complex/imag D
   invokevirtual java/io/PrintStream/println(D)V

   return
.limit stack 12
.limit locals 13
.end method
