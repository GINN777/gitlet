package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import static gitlet.Repository.COMMITS_DIR;
import static gitlet.Utils.*;

public class Commit implements Serializable {

    private String message;
    private Date date;
    private String[] parents;

    /** filename -- SHA-1哈希值 */
    private HashMap<String,String> blobs;

    //构造器
    public Commit(Date date,String message, String parent) {
        this.message = message;
        this.parents = new String[2];
        this.parents[0] = parent;
        this.date=date;
        this.blobs=new HashMap<>();
    }

    Commit(Date date,String message,String parent, HashMap<String,String> blobs){
        this.date=date;
        this.message=message;
        this.parents =new String[2];
        this.parents[0] = parent;
        this.date=date;
        this.blobs=blobs;
        }
    Commit(Date date,String message,String[] parents, HashMap<String,String> blobs){
        this.date=date;
        this.message=message;
        this.parents = new String[2];
        this.parents[0] = parents[0];
        this.parents[1] = parents[1];
        this.blobs = blobs;
    }

    public String getParent(){return parents[0];}

    public String[] getParents(){return parents;}

    public String getMergeParent(){return parents[1];}

    public String getMessage(){return message;}

    public String getFormattedTime(){
        DateFormat dateFormat=new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.CHINESE);
        return dateFormat.format(date);
    }

    /** 将commit对象持久化 写入到文件中  文件名为SHA-1值（ID）  */
    public void save(String ID){
        File commitPrefix=join(COMMITS_DIR,ID.substring(0,2));
        if(!commitPrefix.exists()){
            commitPrefix.mkdir();
        }
        writeObject(join(commitPrefix,ID.substring(2)),this);
    }
    /**
     * 判断文件是否被追踪
     */
    public boolean tracks(String fileName){
        return blobs.containsKey(fileName);
    }

    /**
     * 获取文件版本
     * @param fileName
     * @return
     */
    public String fileVersion(String fileName){
        return blobs.get(fileName);
    }

    public HashMap<String, String> getBlobs() {
        return blobs;
    }

    public boolean isMergeCommit(){
        return parents[1] != null;
    }

    public String toString(){
        return date.toString() + message + blobs.toString() + parents.toString();
    }
}
