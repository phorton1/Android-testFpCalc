package prh.testfpcalc;

import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;



public class Utils
{

    public static String tag = "stdprh";

    public static int global_debug_level = 2;
        // 1 = warnings
        // 2 = linear program flow
        // 3 = first level of loops, etc

    public static String dname = Build.DEVICE;
    public static String platform = dname.equals("vbox86p") ?
        "x86" : "arm";
    public static String mp3s_dir = dname.equals("vbox86p") ?
        "/mnt/shared/mp3s" :
        "/mnt/usb_storage2/mp3s" ;


    //----------------------------------------------------
    // display routines
    //----------------------------------------------------

    public static void log(int debug_level, int indent_level, String msg)
    {
        log(debug_level, indent_level, msg, 1);
    }

    public static void error(String msg)
    {
        log(0,0,"ERROR: " + msg, 1);
        if (true)   // show the error context
        {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int level=4; level<stack.length; level++)
            {
                StackTraceElement e = stack[level];
                Log.d(tag,"... from " + e.getClassName() + "::" + e.getMethodName() + "(" + e.getFileName() + ":" + e.getLineNumber() + ")");

                // optional .. only show one level past our package

                if (true && !e.getClassName().contains("prh")) { break; }
            }
        }
    }

    public static void warning(int debug_level, int indent_level, String msg)
    {
        log(debug_level, indent_level, "WARNING: " + msg, 1);
    }


    protected static void log(int debug_level, int indent_level, String msg, int call_level)
    {

        if (debug_level <= global_debug_level)
        {
            // The debugging filter is by java filename
            // get the incremental level due to the call stack

            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            int level = 0;
            while (level+call_level+4 < stack.length &&
                    stack[level+call_level+4].getClassName().contains("prh"))
            {
                level++;
            }
            // Log.d(tag,"--- level ends up as " + level);

            indent_level += level;

            StackTraceElement caller = stack[call_level+3];
            String filename = caller.getFileName();
            // filename = filename.replaceAll("\\.java$", "");

            String indent = "";
            while (indent_level-- > 0)
            {
                indent += "   ";
            }

            int num = 0;
            String [] parts = msg.split("\\n");
            for (String part:parts)
            {
                Log.d(tag,pad("(" + filename + ":" + caller.getLineNumber() + ")", 27) + " " + indent + (num++>0 ? "~ " : "") + part);
            }
        }
    }


    public static String pad(String in, int len)
    {
        String out = in;
        while (out.length() < len) {out = out + " ";}
        return out;
    }


    //----------------------------------------------------
    // md5 routines
    //----------------------------------------------------

    public static String MD5(String s)
    {
        String retval = null;
        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(s.getBytes("ASCII"));
            byte[] digest = md.digest();
            StringBuffer sb = new StringBuffer();
            for (byte b : digest)
            {
                sb.append(String.format("%02x", b & 0xff));
            }
            retval = sb.toString();
        }
        catch (Exception e)
        {
            error("Exception in MD5 " + e);
        }
        return retval;
    }


    public static String MD5File(String filename)
    {
        String retval = null;
        try
        {
            int numRead;
            byte[] buffer = new byte[1024];
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            InputStream stream =  new FileInputStream(filename);
            do
            {
                numRead = stream.read(buffer);
                if (numRead > 0)
                {
                    md5.update(buffer, 0, numRead);
                }
            } while (numRead != -1);

            stream.close();
            StringBuffer sb = new StringBuffer();
            for (byte b : md5.digest())
            {
                sb.append(String.format("%02x", b & 0xff));
            }
            retval = sb.toString();
        }
        catch (Exception e)
        {
            error("Exception in MD5File " + e);
        }
        return retval;
    }


    public static String join(String delim, String[] array)
    {
        String result = "";
        boolean started = false;
        for (String s:array)
        {
            if (started) result += delim;
            result += s;
            started = true;
        }
        return result;
    }


    //---------------------------------------------
    // call external program
    //---------------------------------------------

    public static String call_exe(String[] args)
    {
        int debug_this = 3;

        StringBuilder blah;

        String result = null;
        Utils.log(debug_this,1,"call_exe: " + " " + join(",",args));

        try
        {
            Process process = new ProcessBuilder()
                    .command(args)
                    .redirectErrorStream(true)
                    .start();
            Utils.log(debug_this+1, 2, "process created");

            InputStream stdin = process.getInputStream();
            OutputStream stdout = process.getOutputStream();
            InputStreamReader isr = new InputStreamReader(stdin);
            BufferedReader br = new BufferedReader(isr);
            Utils.log(debug_this+1, 2, "streams created");

            String line = null;
            while ((line = br.readLine()) != null)
            {
                Utils.log(debug_this+1, 2, "output ==> " + line);
                if (result == null) result = "";
                result += line + "\n";
            }

            int exit_value = process.waitFor();
            Utils.log(debug_this+1, 1, "process_exit_value=" + exit_value);
            if (exit_value != 0) result += "process_exit_value=" + exit_value + "\n";
            process.destroy();
        }
        catch (Exception e)
        {
            Utils.error("Exception calling " + " " + join(",",args) + "' ::" + e);
            if (result != null) result += "exception=" + e + "\n";
        }
        return result;
    }


    //--------------------------------------------------
    // output streams
    //--------------------------------------------------



    public static void closeOutputStream(FileOutputStream ostream)
    {
        try
        {
            ostream.close();
        }
        catch (Exception e)
        {
            Utils.error("Exception closing output stream: " + e);
        }
    }


    public static boolean writeOutput(FileOutputStream ostream, String text)
    {
        // Note that we do not call ostream.write(text.getBytes(), 0, text.length());

        try
        {
            byte[] bytes = text.getBytes();
            ostream.write(bytes, 0, bytes.length);
        }
        catch (Exception e)
        {
            Utils.error("error writing to output stream: " + e);
            return false;
        }
        return true;
    }


    public static FileOutputStream openOpenOutputStream(String filename)
    {
        File ofile = new File(filename);
        FileOutputStream ostream;
        ofile.setWritable(true);
        try
        {
            ostream = new FileOutputStream(ofile);
        }
        catch (Exception e)
        {
            Utils.error("Could not create ostream(" + filename + ")" + e);
            return null;
        }
        return ostream;
    }




}   // class Utils
