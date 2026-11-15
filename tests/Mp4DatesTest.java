package com.hasbreo.still;
import java.io.*;import java.util.*;
public class Mp4DatesTest {
 static byte[] atom(String type,byte[] b)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(out);d.writeInt(b.length+8);d.writeBytes(type);d.write(b);return out.toByteArray();}
 public static void main(String[] args)throws Exception{for(int version=0;version<=1;version++){byte[] h=new byte[32];h[0]=(byte)version;byte[] payload=new byte[]{1,2,3,4,5};File f=File.createTempFile("still-date", ".mp4");try(FileOutputStream o=new FileOutputStream(f)){o.write(atom("moov",atom("mvhd",h)));o.write(atom("mdat",payload));}Mp4Dates.write(f,1700000000123L);try(RandomAccessFile r=new RandomAccessFile(f,"r")){r.seek(20);long actual=version==0?Integer.toUnsignedLong(r.readInt()):r.readLong();if(actual!=1700000000L+2082844800L)throw new AssertionError("Wrong timestamp");r.seek(r.length()-payload.length);byte[] after=new byte[payload.length];r.readFully(after);if(!Arrays.equals(payload,after))throw new AssertionError("Media payload changed");}f.delete();}System.out.println("MP4 v0/v1 dates and media payload preservation passed");}
}
