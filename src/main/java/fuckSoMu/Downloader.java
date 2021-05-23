package fuckSoMu;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kong.unirest.Cookie;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Downloader {

    private static Properties prop = new Properties();
    private static List<Cookie> cookieList =new ArrayList<>();
    private static String saveRoot="";
    private static String we_access_token="";

    public static void main(String[] args){

        if (args.length>0){
            Path p ;
            try {
                p= Paths.get(args[0]);
            } catch (Exception e) {
                p= Paths.get(System.getProperty("user.dir")+File.separator+args[0]);
            }
            propLoader(p);
        }else {
            propLoader();
        }


        System.out.println("Save Path : "+saveRoot);

        System.out.println();
        Scanner sc = new Scanner(System.in);
        System.out.print("Video URL :");
        String[] urlArr = sc.nextLine().split("/");


        getVideo(urlArr[urlArr.length-1]);


    }

    private static void getVideo(String id){

//        remove temp folder
        try {
            Files.walk(Paths.get(saveRoot+"temp"))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
        }

        HttpResponse<String> response = Unirest.get("https://weversewebapi.weverse.io/wapi/v1/communities/3/medias/"+id)
                .header("Authorization", "Bearer "+we_access_token)
                .asString();

        JsonObject mediaObject = JsonParser.parseString(response.getBody()).getAsJsonObject().get("media").getAsJsonObject();
        JsonObject videoObject = mediaObject.get("video").getAsJsonObject();
        String title = mediaObject.get("title").getAsString();
        System.out.println("Video Name : "+title);

        String HLS1080 = videoObject.get("hlsPath").getAsString().replace("HLS","HLS_1080");
        String mediaRoot = videoObject.get("hlsPath").getAsString().replace("HLS.m3u8","");
        System.out.println(videoObject);
//        System.out.println(HLS1080);

        JsonArray subArr = videoObject.get("captionS3Paths").getAsJsonArray();
//        System.out.println(subArr);

        HttpResponse<String> HLS = Unirest.get(HLS1080)
                .cookie(cookieList)
                .asString();
        if(HLS.getStatus()==404){
            System.out.println(HLS1080.replace("HLS_1080","HLSFHD"));
            HLS = Unirest.get(HLS1080.replace("HLS_1080","HLSFHD"))
                    .cookie(cookieList)
                    .asString();
        }

        DownloadTS(HLS.getBody().split("\\n"),mediaRoot,title);
        DownloadSubtitles(subArr,title);

        Scanner sc = new Scanner(System.in);
        System.out.print("Enter new video url to continue download or ENTER to exit :");
        String input =sc.nextLine();
        if(input.contains("weverse")){
            String[] urlArr = input.split("/");
            getVideo(urlArr[urlArr.length-1]);
        }else{
            System.exit(0);
        }

    }


    private static void DownloadSubtitles(JsonArray subArray, String mediaTitle){
        for (int i=0;i<subArray.size();i++) {
            JsonObject obj = subArray.get(i).getAsJsonObject();
            String filePath = saveRoot+File.separator+mediaTitle+File.separator+mediaTitle+"."+obj.get("languageCode").getAsString()+".srt";

            Unirest.get(obj.get("captionFilePath").getAsString())
                    .cookie(cookieList)
                    .asFile(filePath);

            if(obj.get("languageCode").getAsString().contains("CN")){
                chineseConverter(filePath);
            }
        }
    }

    private static void chineseConverter(String filePath){

        List<String> allText = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(filePath))) {
            stream.forEach(ln-> allText.add(ZhConverterUtil.toTraditional(ln)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            Path path = Paths.get(filePath.replace("-CN","-HK"));
            byte[] strToBytes = String.join("\n",allText).getBytes();
            Files.write(path, strToBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void DownloadTS(String[] rows, String mediaRoot,String mediaTitle){
        new File(saveRoot+"temp").mkdirs();
        new File(saveRoot+mediaTitle).mkdirs();
        System.out.print("Downloading : ");
        for (String row :rows) {
            System.out.println(row);
            if(row.contains(".ts")){
                System.out.print("|");
                Unirest.get(mediaRoot+row)
                        .cookie(cookieList)
                        .asFile(saveRoot+"temp"+File.separator+row.substring(row.length()-7));
            }
        }
        System.out.println("");
        mergeConvertVideo(mediaTitle);

    }

    private static void mergeConvertVideo(String mediaTitle) {
//        String ffmpeg = fuckSoMu.Downloader.class.getClassLoader().getResource("ffmpeg.exe").getPath();
        String ffmpeg = System.getProperty("user.dir")+File.separator+"ffmpeg.exe";

//        try {
//            Files.copy(Paths.get(Downloader.class.getClassLoader().getResource("ffmpeg.exe").getPath()),
//                    Paths.get(System.getProperty("user.dir")+File.separator+"ffmpeg.exe"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        ProcessBuilder pb1 = new ProcessBuilder();
        String quot="";         //"\\\"";
        pb1.command("cmd.exe", "/c", "copy /b "+ quot+saveRoot+"temp"+File.separator+"*.ts"+quot +" "+ quot+saveRoot+"temp"+File.separator+"all.ts"+quot );

        try {
            Process merge = pb1.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(merge.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
//                System.out.println(line);
            }
            int exitCode = merge.waitFor();
//            System.out.println("\nExited with error code : " + exitCode);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ProcessBuilder pb2 = new ProcessBuilder();

        pb2.redirectErrorStream(true);
        pb2.command(ffmpeg,
                "-i",quot+saveRoot+"temp"+File.separator+"all.ts"+quot,
                "-acodec",
                "copy",
                "-vcodec",
                "copy",
                quot+saveRoot+mediaTitle+File.separator+mediaTitle+".mp4"+quot);

        pb2.command();
        try {
            Process convert = pb2.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(convert.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            int exitCode = 0;
            try {
                exitCode = convert.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("\nExited with error code : " + exitCode);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Complete");
    }

    private static void propLoader() {
        try {
            propLoader(Paths.get(Downloader.class.getClassLoader().getResource("config.txt").toURI()));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    private static void propLoader(Path path) {
        System.out.println(System.getProperty("user.dir"));

        Map<String,String> prop = new HashMap<>();

        try {

            Files.lines(path).forEach(line->{
                String[] a =line.split("=", 2);
                prop.put(a[0],a[1]);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        String[] cookieArr = prop.get("cookie").replace("\"","").split("; ");

        saveRoot=prop.get("path");
        if(!saveRoot.endsWith(File.separator)){
            saveRoot+=File.separator;
        }

        for (String elem:cookieArr) {
            String[] tempArr =elem.split("=");
            cookieList.add(new Cookie(tempArr[0],tempArr[1]));
            if(tempArr[0].equals("we_access_token")){
                we_access_token=tempArr[1];
            }
        }
    }
}
