package com.hasbreo.still;
import java.io.*;
final class IO {static void copy(InputStream in,OutputStream out)throws IOException{byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();copy(in,b);return b.toByteArray();}}
