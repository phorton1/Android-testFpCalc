package prh.testfpcalc;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// This program tests the executables and shared libraries
// built in /fpcalc/chromaprint, etc.  That process copies
// the executables onto the various Android devices into
// the /data/local/tmp directory, and we call them directly
// from there.
//
// So this program is dependent on the /fpcalc/chromaprint
// build process


public class MainActivity extends Activity
{
    // if do_one_filename_solo is set, just that filename will be done
    // if do_one_filename_re is set, only paths matching the re will be done
    // otherwise all paths in mp3 directory scan will be done.

    String do_one_filename_solo = "albums/Blues/New/Blues By Nature - Blue To The Bone/01 - Cadillac Blues.mp3";
            // working test case

    String do_one_filename_re = "";
        // "albums\\/Blues\\/New\\/Blues By Nature - Blue To The Bone\\/01 - Cadillac Blues\\.mp3";
            // working test case
        // "singles\\/Rock\\/Alt\\/Modest Mouse - Interstate 8\\/Broke\\.mp3";
            // this one crashed win 0.9 builds, but seems to work as espected
            // (parses but gives warnings/errors) on java

        // ".*(" +
        // "albums\\/Compilations\\/Various - Unknown/If I Had .1000000.*|" +
        // "albums\\/Favorite\\/Dan Hicks & the Acoustic Warriors - Shootin' Straight\\/15 - .100,000\\.mp3|" +
        // "albums\\/Rock\\/Alt\\/Soul Coughing - El Oso\\/08 - .300 Soul\\.wma" + ")";
            // these three failed on x86 (ubuntu) Perl because we had
            // to escape the $ when calling shell. They work fine in java.
        // ".*albums\\/Compilations\\/Various - Unknown\\/Wild.*\\.MP3";
            // get different names in Perl on Windows and Ubuntu for this file
            // In java it returned the proper UTF8 filename which worked.

    boolean SHOW_ONE_FILENAME_RE_INFO = true;
        // if true, will show the results for one_filename_re calls
        // otherwise, may be easier to read if just shows calls


    String[] do_versions = {
            "0.9",
            // "0.11",
            // "2.7",
    };

    String[] do_executables =  {
        "fpcalc_linux_x86",
        // "fpcalc_win_x86",
        // "fpcalc_linux_arm7",
        // "fpcalc_win_arm7",
        // "fpcalc_linux_arm",
        // "fpcalc_win_arm",
    };


    String[] do_libraries = {
        "fpcalc_linux_x86s",
        // "fpcalc_win_x86s",
        // "fpcalc_linux_arm7s",
        // "fpcalc_win_arm7s",
        // "fpcalc_linux_arms",
        // "fpcalc_win_arms",
    };



    //---------------------------------------
    // main
    //---------------------------------------

    String the_id = "";
    String display_id = "";
    String display_path = "initializing..";
    Timer the_timer = null;
    myTimerTask the_task = null;
    ArrayList<String> paths = new ArrayList<String>();


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Utils.log(2,0,"test_fpCalc::onCreate() called");
        the_task = new myTimerTask();
        the_timer = new Timer("myTimer");
        the_timer.schedule(the_task,2000);
        status_message();

        String property = System.getProperty("java.library.path");
        StringTokenizer parser = new StringTokenizer(property, ";");
        while (parser.hasMoreTokens()) {
            Utils.log(0,1,"system_path=" + parser.nextToken());
        }

        Utils.log(2,0,"test_fpCalc::onCreate() returning");
    }


    public native String fpCalc(String[] args);
        // declare this in your java class (in my case, MainActivity)


    String set_id(String version, String exe, String lib)
    {
        the_id = (exe != "") ? exe : lib;
        the_id += "." + version;

        // call it to get and check the version

        Utils.log(0,1,"get version(" + the_id + ")");
        String ffinfo = call_one(version,exe,lib,"");
        if (do_one_filename_re == "" || SHOW_ONE_FILENAME_RE_INFO)
        {
            Utils.log(0,1,"version_info=" + ffinfo);
        }

        String ffversion = "";
        Pattern pattern = Pattern.compile("ffmpeg_version=\"(.*)\"");
        Matcher matcher = pattern.matcher(ffinfo);
        if (matcher.find()) ffversion = matcher.group(1);
        Utils.log(0,1,"internal_version=" + ffversion);
        ffversion = ffversion.replaceAll("\"","");
        ffversion = ffversion.replaceAll("^n","");
        if (ffversion.startsWith("2.7"))
            ffversion = ffversion.replaceAll("-.*$","");

        if (!ffversion.equals(version))
        {
            Utils.error("VERSION PROBLEM: expected("+version+") got("+ffversion+") from "+the_id);
            return "";
        }
        return ffinfo;
    }

    void status_message()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                TextView tv_exe = (TextView) findViewById(R.id.status_exe);
                tv_exe.setText(display_id);
                TextView tv_path = (TextView) findViewById(R.id.status_path);
                tv_path.setText(display_path);
            }
        });
    }

    //-------------------------------
    // a separate task
    //-------------------------------
    // to keep the busy process off the main thread

    private class myTimerTask extends TimerTask
    {
        Boolean ok = true;
        MainActivity parent;
        public void run()
        {
            Utils.log(2,0,"test_fpCalc::myTimerTask::run() called");

            // get the paths if not doing a solo filename
            // pass the pattern if ther is a do_one_filename_re

            if (do_one_filename_solo.isEmpty())
            {
                Pattern pattern = null;
                if (!do_one_filename_re.isEmpty())
                {
                    try
                    {
                        pattern = Pattern.compile(do_one_filename_re,Pattern.CASE_INSENSITIVE);
                    }
                    catch (Exception e)
                    {
                        Utils.error("Could not compile re pattern: " + e);
                        return;
                    }
                }

                Utils.log(0,0,"getting paths ...");
                get_paths(Utils.mp3s_dir,pattern);
                if (paths.size() > 0)
                    Utils.log(0,1,"found " + paths.size() + " paths");
                else
                {
                    Utils.error("No paths found!!");
                    ok = false;
                }
            }

            // loop through all libraries and exectuables

            if (ok)
            {
                for (String lib : do_libraries)
                {
                    if (lib.contains(Utils.platform))
                    {
                        for (String version : do_versions)
                        {
                            if (!do_one_exe_or_lib(version,"",lib))
                            {
                                return;
                            }
                        }
                    }
                }
                for (String exe : do_executables)
                {
                    if (exe.contains(Utils.platform))
                    {
                        for (String version : do_versions)
                        {
                            if (!do_one_exe_or_lib(version,exe,""))
                            {
                                return;
                            }
                        }
                    }
                }
            }

            Utils.log(2,0,"test_fpCalc::myTimerTask::run() finished");

        }   // myTimerTask::Run()
    }   // class myTimerTask


    //------------------------------------------
    // get_paths
    //------------------------------------------


    public boolean isAudioFile(String fn)
    {
        String lfn = fn.toLowerCase();
        if (lfn.endsWith(".mp3") ||
            lfn.endsWith(".wma") ||
            lfn.endsWith(".m4a") ||
            lfn.endsWith(".mp3"))
        {
            return true;
        }
        return false;
    }

    public void get_paths( String path, Pattern match_it)
        // do recursive diretory scan to get paths
        // use match_it if not null
    {
        File root = new File( path );
        File[] list = root.listFiles();
        if (list == null) return;

        for ( File f : list )
        {
            if ( f.isDirectory())
            {
                get_paths(f.getAbsolutePath(),match_it);
            }
            else
            {
                String fn = f.getAbsoluteFile().toString();
                if (isAudioFile(fn))
                {
                    boolean use_it = true;
                    if (match_it != null)
                    {
                        Matcher matcher = match_it.matcher(fn);
                        use_it = matcher.find();
                    }
                    if (use_it)  paths.add(fn);
                }
            }
        }
    }



    //------------------------------------------
    // do_one
    //------------------------------------------
    // do a "full test" for one exe or lib
    // which either calls gen_results(), or test_one()

    boolean do_one_exe_or_lib(String version, String exe, String lib)
    {
        if (lib != "")
        {
            String short_lib_name = lib + "." + version;
            String long_lib_name = "lib" + short_lib_name + ".so";

            // libs used to be in jniLibs directory
            // System.loadLibrary(short_lib_name);

            try
            {
                System.load("/data/local/tmp/" + long_lib_name);
            }
            catch (UnsatisfiedLinkError e)
            {
                Utils.error("Could not load library " + long_lib_name + " : " + e);
                return false;
            }
        }
        if (do_one_filename_solo != "")
        {
            if (!test_one(version, exe, lib, do_one_filename_solo))
                return false;
        }
        else
        {
            if (!gen_results(version, exe, lib))
                return false;
        }
        return true;
    }


    //------------------------------------------
    // test_one
    //------------------------------------------
    // just test one file for the given exe or lib

    boolean test_one(String version, String exe, String lib, String filename)
    {
        String use_filename = "";
        try
        {
            use_filename = new String(filename.getBytes() , "utf-8");
        }
        catch (Exception e)
        {
            Utils.error("Could not decode filename=" + filename + " Exception=" + e);
            return false;
        }

        if (set_id(version,exe,lib)=="") return false;
        String text = call_one(version,exe,lib,Utils.mp3s_dir + "/" + use_filename);
        Utils.log(0, 1, "result=" + text );
        return (text.equals("") ? false : true);
    }

    //---------------------------------------------
    // gen_results
    //---------------------------------------------
    // test all paths for the given exe or lib
    // does not output to file if do_one_filename_re != ""

    boolean gen_results(String version, String exe, String lib)
    {
        Utils.log(0,0,"gen_results(" + exe + "," + lib + ")");
        String ffinfo = set_id(version,exe,lib);
        if (ffinfo == "") return false;
        FileOutputStream ostream = null;

        // open the output stream if !do_one_filename_re

        if (do_one_filename_re == "")
        {
            String output_filename = Utils.mp3s_dir + "/" + the_id + ".txt";
            File ofile = new File(output_filename);
            if (ofile.exists())
            {
                Utils.error("FILE ALREADY EXISTS (skipping): " + output_filename);
                return true;
            }
            ostream = Utils.openOpenOutputStream(output_filename);
            if (ostream == null) return false;
        }

        // dump version to the text file
        // then process the records
        // possibly show the result
        // possibly dump the result to the file

        ffinfo += "\n\n";
        if (do_one_filename_re != "" ||
            Utils.writeOutput(ostream,ffinfo))
        {
            for (int i = 0; i < paths.size(); i++)
            {
                String filename = paths.get(i);
                String text = call_one(version,exe,lib,filename);

                if (do_one_filename_re != "" && SHOW_ONE_FILENAME_RE_INFO)
                    Utils.log(0,2,"results=" + text);
                if (do_one_filename_re == "" && !Utils.writeOutput(ostream,text))
                    return false;
            }
        }

        // close output stream if needed

        if (do_one_filename_re == "")
            Utils.closeOutputStream(ostream);

        return true;

    }   // gen_results()



    //---------------------------------------------
    // call_one
    //---------------------------------------------
    // "low level" call one exe or lib with a filename
    // if filename="", calls with "-version"

    String call_one(String version, String exe, String lib, String filename)
    {
        String retval = "";
        String path = filename == "" ? "-version" : filename;

        display_path = filename;
        display_id   = (lib == "" ? "exec: " : "call: ") + the_id;
        status_message();

        String mem_message = "";
        if (true)   // set to true to debug native memory problems
        {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            activityManager.getMemoryInfo(mi);
            long availableMegs = mi.availMem / 1048576L;
            mem_message = "MEM(" + availableMegs + ") ";
        }

        // exes used to be in /system/bin and
        // String exe_name = "/system/bin/" + exe + "." + version;

        if (exe != "")
        {
            String exe_name = "/data/local/tmp/" + exe + "." + version;
            Utils.log(0,0,mem_message + "calling " + exe_name + "(" + path + ")");
            String[] args = {exe_name, "-md5","-stream_md5","-ints", path};
            retval = Utils.call_exe(args);
        }
        else
        {
            try
            {
                Utils.log(0,0,mem_message + "fpcalc() " + the_id + "(" + path + ")");
                String[] args = {"-md5","-stream_md5","-ints",path};
                retval = fpCalc(args);
                // Utils.log(0,1,"retval=" + retval);
            }
            catch (Exception e)
            {
                Utils.error("Exception calling " + the_id + ": " + e);
            }
        }
        if (retval == "")
            retval = "NO RESULTS from " + the_id + " " + filename + "\n";
        else if (filename != "")
            retval = the_id + "(" + path + ")\n" + retval + "\n\n";

        return retval;

    }   // call_one()


}   // class MainActivity
