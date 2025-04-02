package gitlet;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static gitlet.Utils. *;

public class Repository {
    //当前工作目录
    private static final File CWD=new File(System.getProperty("user.dir"));
    //.gitlet目录
    private static final File GITLET_DIR=join(CWD,".gitlet");
    /**
     * The .gitlet/objects directory.
     */
    public static final File HEAD = join(GITLET_DIR, "HEAD");
    /**
     * The .gitlet/objects directory.
     */
    public static final File INDEX = join(GITLET_DIR, "INDEX");
    /**
     * The .gitlet/branches directory.
     */
    public static final File BRANCHES_DIR = join(GITLET_DIR, "branches");
    /**
     * The .gitlet/objects directory.
     */
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    /**
     * The .gitlet/objects/blobs  directory.
     */
    public static final File BLOBS_DIR = join(OBJECTS_DIR, "blobs");
    /**
     * The .gitlet/objects/commits  directory.
     */
    public static final File COMMITS_DIR = join(OBJECTS_DIR, "commits");

    /**
     * 完成initial commit
     *  GITLET_DIR.mkdir()方法创建.gitlet文件夹
     *  创建所需的其他文件夹。。。
     *
     */
    public static void init(){
        //如果.gitlet文件夹已经存在，则抛出异常
        if(GITLET_DIR.exists()){
            System.out.println("Already a Gitlet version-control system in the current directory.");
            System.exit(0);
        }

        //创建.gitlet文件夹及其子目录
        GITLET_DIR.mkdir();
        BRANCHES_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        BLOBS_DIR.mkdir();
        COMMITS_DIR.mkdir();

        //创建并保存initial commit
        Commit initCommit=new Commit(new Date(0),"initial commit",null);
        String ID=sha1(initCommit.toString());
        initCommit.save(ID);
        //保存master branch和HEAD
        writeContents(join(BRANCHES_DIR,"master"),ID);
        writeContents(HEAD,"master");
    }

    //检查当前目录下是否存在.gitlet文件夹  将此操作封装成一个方法 方便后面多次调用
    private static void checkIfGitletExists() {
        if(!GITLET_DIR.exists()){
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }

    public static void add(String FileName){
        checkIfGitletExists();
        File newFile=join(CWD,FileName);
        if(!newFile.exists()){
            System.out.println("File does not exist.");
            System.exit(0);
        }

        //新文件添加 保存blob
        //读取文件内容 计算sha1值
        byte[] fileContent=readContents(newFile);
        String ID=sha1(fileContent);
        //构建blob存储的目录路径
        File blobPrefix=join(BLOBS_DIR,ID.substring(0,2));
        //如果不存在 就创建blob存储目录
        if(!blobPrefix.exists()){
            blobPrefix.mkdir();
        }
        //构建blob文件的完整路径   将blobPrefix目录的哈希值ID 的剩余部分连接起来，形成完成的blob文件路径
        File blob=join(blobPrefix,ID.substring(2));
        //检查blob文件是否存在
        if(!blob.exists()){
            //不存在：将文件内容写入blob文件
            writeContents(blob,fileContent);
        }
        //存在：则说明这个blob文件已经被存储过 ，不需要重复存储

        //更新索引Index
        //首先获取当前分支的最新提交(head)
        Commit headCommit=getHeadCommit();
        Index stagingArea=Index.getStagingArea();
        //1. 如果文件在removed映射中 并且哈希值相同 则从removed映射中删除
        if(stagingArea.removed.containsKey(FileName)
            && stagingArea.removed.get(FileName).equals(ID)){
            stagingArea.removed.remove(FileName);
            //2. 如果是新文件
        } else if (!stagingArea.staged.containsKey(FileName)) {
            //如果文件不在staged映射中，检查他是否被最新的commit追踪
            if(!headCommit.tracks(FileName)||!headCommit.fileVersion(FileName).equals(ID)){
                //没有被追踪或者版本不同，则将其添加到staged映射中
                stagingArea.staged.put(FileName,ID);
            } else {
                //如果文件在head commit中已暂存 且版本一致，则不需要再次暂存 直接返回
                return;
            }
            //3. 如果文件已暂存 但内容更改
        } else if (!stagingArea.staged.get(FileName).equals(ID)) {
            //如果文件已被暂存，但内容的哈希值不同，则更新staged中的哈希值
            stagingArea.staged.put(FileName,ID);
        }else return;
        stagingArea.save();

    }

    /**
     * 返回head指向的commit
     */
    private static Commit getHeadCommit(){
        String curBranch=readContentsAsString(HEAD);
        String SHA1=getHeadCommitID(curBranch);
        return getCommitBySHA(SHA1);
    }
    /**
     * 获取给定分支的HEAD的SHA1值
     */
    private static String getHeadCommitID(String branchName){
        return readContentsAsString(join(BRANCHES_DIR,branchName));
    }
    /**
     * 根据SHA1值获取commit对象
     */
    private static Commit getCommitBySHA(String SHA1) {
        File commitPrefix=join(COMMITS_DIR,SHA1.substring(0,2));
        File commit=join(commitPrefix,SHA1.substring(2));
        if(!commit.exists()){
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        return readObject(commit,Commit.class);
    }

    /**
     * 根据给定的提交（Commit）对象和暂存区变化（Index）来创建一个新的 blob 映射。
     * 这个新的 blob 映射代表了提交中文件的最新状态，包括新增、修改和删除的文件
     * newBlobs中不包括在working dir中但未在staging area中的文件
     * 即 只包括add到staging area中的文件 因为只有add后才会生成blob
     * **这里注意区别snapShot方法 snapshot方法是对working dir中的所有文件生成快照（blob)
     * @param commit
     * @param changes
     * @return
     */
    private static HashMap<String,String> getNewBlobs(Commit commit,Index changes){
        //获取给定的commit的当前的blob映射
        HashMap<String,String> orig= commit.getBlobs();
        //使用当前的 blob 映射进行初始化。这个新的映射将用于存储更新后的 blob 信息
        HashMap<String,String> newBlobs=new HashMap<>(orig);
        //遍历Index对象中的staged映射， 里面包含了暂存区中所有新增的或修改的文件
        for (Map.Entry<String,String> entry : changes.staged.entrySet()){
            //对于每个文件 将其blob哈希值添加到新的blob映射中 （如果是相同文件但被修改 则新的(value)SHA-1哈希值会覆盖原来的哈希值）
            newBlobs.put(entry.getKey(),entry.getValue());
        }
        //遍历Index对象中的removed映射，里面包含了暂存区中被删除的文件
        for (String removedFile : changes.removed.keySet()){
            //对于每个被删除的文件，从新的blob映射中移除
            newBlobs.remove(removedFile);
        }
        return newBlobs;
    }


    /**
     * commit命令
     */
    public static void commit(String message){
        /**  Precheck.  */
        checkIfGitletExists();
        if(message.equals("")){
            System.out.println("Please enter a commit message.");
            System.exit(0);
        }
        //如果暂存区为空(没有变化)则不能commit
        Index changes=Index.getStagingArea();
        if(changes.isEmpty()){
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }

        /** 构建并保存新的提交 */
        /**
         * 几个关键函数： 通过分支名得到该分支的HEAD提交（getHeadCommitID）   通过SHA1值得到commit对象（getCommitBySHA）
         */
        Date timeStamp=new Date();
        String curBranch = readContentsAsString(HEAD);

        String curCommitID = getHeadCommitID(curBranch);  //parent为sha1值
        //Blobs     根据sha-1值得到commit对象--->通过commit对象和index(暂存区的变化)得到newBlobs
        Commit prevCommit=getCommitBySHA(curCommitID);
        HashMap<String, String> newBlobs = getNewBlobs(prevCommit, changes);

        //检查是否为merge冲突后的提交
        String[] parents;
        File mergeHeadFile = join(GITLET_DIR, "MERGE_HEAD");
        if(mergeHeadFile.exists()){
            String mergedCommitID = readContentsAsString(mergeHeadFile);
            parents=new String[]{mergedCommitID,curCommitID};
        }else {
            parents=new String[]{curCommitID};
        }
        //创建并保存新的commit
        Commit newCommit=new Commit(timeStamp,message,parents,newBlobs);
        String ID=sha1(newCommit.toString());
        try {
            newCommit.save(ID);
            writeContents(join(BRANCHES_DIR,curBranch),ID);
        } catch (Exception e) {
            System.out.println("Error saving commit: " + e.getMessage());
            System.exit(1);
        }
        /** 清理合并状态和暂存区 */
        if(mergeHeadFile.exists()){
            if (!mergeHeadFile.delete()) {
                System.out.println("Error deleting MERGE_HEAD");
                System.exit(1);
            }
            System.out.println("Merge commit completed");
        }
        changes.clear();
        changes.save();
    }

    /**
     * rm 命令
     */
    public static void remove(String fileName){
        checkIfGitletExists();
        //Flags: 如果文件 未被追踪也不在StagingArea中(neither staged nor tracked) 则为true
        boolean errorFlag=true;

        Index changes=Index.getStagingArea();
        //如果在staging area则直接unstage掉
        if(changes.staged.containsKey(fileName)){
            changes.staged.remove(fileName);
            errorFlag=false;
        }

        Commit headCommit=getHeadCommit();
        //如果文件已被tracked 则将它stage到index的removal里面 再将其从working dir移除
        if(headCommit.tracks(fileName)){
            changes.removed.put(fileName,headCommit.fileVersion(fileName));
            File toDelete =join(CWD,fileName);
            if(toDelete.exists()&&!restrictedDelete(toDelete)){
                //如果restrictedDelete返回false 则表示文件不能删除 然后执行下面语句退出
                System.exit(0);
            }
            errorFlag=false;
        }
        //如果既未被track也未被stage 则报错
        if(errorFlag){
            System.out.println("No reason to remove the file.");
            System.exit(0);
        }
        changes.save();
    }

    /**
     * 打印commit的详情信息
     * @param ID
     * @param commit
     */
    private static void printCommit(String ID,Commit commit){
        StringBuilder returnSB=new StringBuilder();
        returnSB.append("===\n");
        returnSB.append("commit ").append(ID).append("\n");
        //如果时merge Commit则返回父提交的前7个字符
        if(commit.isMergeCommit()){
            returnSB.append(
                    "Merge: " + commit.getParent().substring(0, 7) + " " + commit.getMergeParent().substring(0, 7) + "\n"
            );
        }
        returnSB.append("Date: " + commit.getFormattedTime() + "\n");
        returnSB.append(commit.getMessage() + "\n");
        returnSB.append("\n");
        System.out.print(returnSB.toString());
    }

    /**
     * log 命令
     * 获取当前分支的提交历史
     */
    public static void log(){
        checkIfGitletExists();
        //获取当前分支名称
        String curBranch = readContentsAsString(HEAD);
        //通过当前分支 获得最新commit的SHA-1值
        String ID = getHeadCommitID(curBranch);
        //通过ID（SHA-1） 获得commit对象
        Commit curCommit=getCommitBySHA(ID);
        //打印该节点信息
        printCommit(ID,curCommit);
        String parentID = curCommit.getParent();
        //如果该节点有父节点 则递归打印父节点信息 直到initial commit(其parent为null)
        while (parentID!=null){
            curCommit=getCommitBySHA(parentID);
            ID=parentID;
            printCommit(ID,curCommit);
            parentID=curCommit.getParent();
        }
    }

    /**
     * global-log 命令
     * 打印所有commit的详情信息
     */
    public static void globalLog(){
        checkIfGitletExists();
        //获取commit目录下所有子目录名（这些名称为commit哈希值的前两位
        String[] commitDirs= COMMITS_DIR.list();
        //遍历每个子目录(每个子目录（哈希值前两位的）下都存放着若干个commit对象)
        for(String commitDir : commitDirs){
            //对于每个子目录 使用plainFilenamesIn方法获取其下所有普通文件名（这些名称即commit哈希值的剩余部分
            List<String> commits=plainFilenamesIn(join(COMMITS_DIR,commitDir));
            for(String commit : commits){
                String ID=commitDir+commit;
                Commit commitObj = getCommitBySHA(ID);
                printCommit(ID,commitObj);
            }
        }
    }

    /**
     * 为当前工作目录（working dir)中的所有文件（无论有没有add或commit） 创建blobs 拍摄快照
     */
    private static HashMap<String,String> takeSnapshot(){
        List<String> curFiles=plainFilenamesIn(CWD);
        HashMap<String,String> snapShot =new HashMap<>();
        for(String fileName : curFiles){
            //根据文件内容生成SHA-1值 将其存到hashmap中
            byte[] fileContent = readContents(join(CWD,fileName));
            String ID=sha1(fileContent);
            snapShot.put(fileName,ID);
        }
        return snapShot;
    }

    /**
     * status 命令
     *
     * 使用多线程进行优化，开启线程池分别执行各类文件的查找
     * 对于一个数量较少（大小110kb）的文件 优化前：110ms   优化后：85ms
     */
/*      优化前：
    public static void status(){
        long startTime = System.nanoTime();
        checkIfGitletExists();
        StringBuilder returnSB = new StringBuilder();

        */
        /** Branches. *//*

        //获取当前branch
        String curBranch = readContentsAsString(HEAD);
        //获取所有branch
        List<String> branches=plainFilenamesIn(BRANCHES_DIR);
        returnSB.append("=== Branches ===\n");
        //打印当前分支
        returnSB.append("*").append(curBranch).append("\n");
        //打印除当前分支以外的其他分支
        for(String branch : branches){
            if(!branch.equals(curBranch)){
                returnSB.append(branch).append("\n");
            }
        }
        returnSB.append("\n");

        */
        /** Staged Files *//*

        //获取暂存区状态
        Index changes=Index.getStagingArea();

        String[] stagedFiles = changes.staged.keySet().toArray(new String[0]);
        Arrays.sort(stagedFiles);
        returnSB.append("=== Staged Files ===\n");
        for(String stagedFile : stagedFiles){
            returnSB.append(stagedFile).append("\n");
        }
        returnSB.append("\n");

        */
        /** Removed Files *//*

        String[] removedFiles = changes.removed.keySet().toArray(new String[0]);
        Arrays.sort(removedFiles);
        returnSB.append("=== Removed Files ===\n");
        for(String removedFile : removedFiles){
            returnSB.append(removedFile).append("\n");
        }
        returnSB.append("\n");

        */
        /** Modifications Not Staged For Commit *//*

        //这里的modified的文件是指 当前HEAD commit追踪 但在working dir中更改 但还未被添加进staging area中的文件
        returnSB.append("=== Modifications Not Staged For Commit ===\n");
        HashMap<String, String> newBlobs = getNewBlobs(getHeadCommit(), changes);
        HashMap<String,String> snapshot = takeSnapshot();
        //TODO: 为什么用treeSet?
        TreeSet<String> modifiedFiles = new TreeSet<>();
        for(Map.Entry<String,String> entry : newBlobs.entrySet()){
            //如果在snapshot中存在 并且 snapshot中的sha-1值和newBlobs中不一样--->证明文件被修改
            if(snapshot.containsKey(entry.getKey()) && !snapshot.get(entry.getKey()).equals(entry.getValue())){
                modifiedFiles.add(entry.getKey()+" (modified)");
            } else if (!snapshot.containsKey(entry.getKey())) {
                //如果snapshot中不存在newBlob中的文件--->证明文件被删除
                modifiedFiles.add(entry.getKey()+" (deleted)");
            }
        }
        for(String entry : modifiedFiles){
            returnSB.append(entry).append("\n");
        }
        returnSB.append("\n");

        */
        /** ----just in case i got confused again next time~----
         * 关于在modifiedFile和untracked中 for循环遍历的对象不一样：  因为两者的执行逻辑有区别
         * Modified Files：遍历 newBlobs 是为了找出那些在版本控制系统中已有记录，但在工作目录中被修改且未暂存的文件。
         * Untracked Files：遍历 snapshot 是为了找出那些在工作目录中存在，但在版本控制系统中没有任何记录的全新文件。
         *//*
        */
        /** Untracked Files *//*

        returnSB.append("=== Untracked Files ===\n");
        TreeSet<String> untracked=new TreeSet<>();
        for(Map.Entry<String,String> entry : snapshot.entrySet()){
            if(!newBlobs.containsKey(entry.getKey())){
                untracked.add(entry.getKey());
            }
        }
        for(String entry : untracked){
            returnSB.append(entry).append("\n");
        }
        returnSB.append("\n");

        System.out.println(returnSB.toString());
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000; // 将纳秒转为毫秒
        System.out.println("Execution time (unmodified): " + duration + " ms");
    }
*/

    public static void status() {
            checkIfGitletExists();
            StringBuilder returnSB = new StringBuilder();

            ExecutorService executor = Executors.newFixedThreadPool(4);

            try {
                // Branches Task
                Callable<String> branchesTask = () -> {
                    StringBuilder sb = new StringBuilder();
                    String curBranch = readContentsAsString(HEAD);
                    List<String> branches = plainFilenamesIn(BRANCHES_DIR);
                    sb.append("=== Branches ===\n");
                    sb.append("*").append(curBranch).append("\n");
                    for (String branch : branches) {
                        if (!branch.equals(curBranch)) {
                            sb.append(branch).append("\n");
                        }
                    }
                    sb.append("\n");
                    return sb.toString();
                };

                // Staged Files Task
                Callable<String> stagedFilesTask = () -> {
                    StringBuilder sb = new StringBuilder();
                    Index changes = Index.getStagingArea();
                    String[] stagedFiles = changes.staged.keySet().toArray(new String[0]);
                    Arrays.sort(stagedFiles);
                    sb.append("=== Staged Files ===\n");
                    for (String stagedFile : stagedFiles) {
                        sb.append(stagedFile).append("\n");
                    }
                    sb.append("\n");
                    return sb.toString();
                };

                // Removed Files Task
                Callable<String> removedFilesTask = () -> {
                    StringBuilder sb = new StringBuilder();
                    Index changes = Index.getStagingArea();
                    String[] removedFiles = changes.removed.keySet().toArray(new String[0]);
                    Arrays.sort(removedFiles);
                    sb.append("=== Removed Files ===\n");
                    for (String removedFile : removedFiles) {
                        sb.append(removedFile).append("\n");
                    }
                    sb.append("\n");
                    return sb.toString();
                };

                // Modifications Not Staged For Commit Task
                Callable<String> modificationsTask = () -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== Modifications Not Staged For Commit ===\n");
                    Index changes = Index.getStagingArea();
                    HashMap<String, String> newBlobs = getNewBlobs(getHeadCommit(), changes);
                    HashMap<String, String> snapshot = takeSnapshot();
                    TreeSet<String> modifiedFiles = new TreeSet<>();
                    for (Map.Entry<String, String> entry : newBlobs.entrySet()) {
                        if (snapshot.containsKey(entry.getKey()) && !snapshot.get(entry.getKey()).equals(entry.getValue())) {
                            modifiedFiles.add(entry.getKey() + " (modified)");
                        } else if (!snapshot.containsKey(entry.getKey())) {
                            modifiedFiles.add(entry.getKey() + " (deleted)");
                        }
                    }
                    for (String entry : modifiedFiles) {
                        sb.append(entry).append("\n");
                    }
                    sb.append("\n");
                    return sb.toString();
                };

                // Untracked Files Task
                Callable<String> untrackedFilesTask = () -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== Untracked Files ===\n");
                    HashMap<String, String> snapshot = takeSnapshot();
                    HashMap<String, String> newBlobs = getNewBlobs(getHeadCommit(), Index.getStagingArea());
                    TreeSet<String> untracked = new TreeSet<>();
                    for (Map.Entry<String, String> entry : snapshot.entrySet()) {
                        if (!newBlobs.containsKey(entry.getKey())) {
                            untracked.add(entry.getKey());
                        }
                    }
                    for (String entry : untracked) {
                        sb.append(entry).append("\n");
                    }
                    sb.append("\n");
                    return sb.toString();
                };

                // Execute tasks
                Future<String> branchesFuture = executor.submit(branchesTask);
                Future<String> stagedFilesFuture = executor.submit(stagedFilesTask);
                Future<String> removedFilesFuture = executor.submit(removedFilesTask);
                Future<String> modificationsFuture = executor.submit(modificationsTask);
                Future<String> untrackedFilesFuture = executor.submit(untrackedFilesTask);

                // Collect results
                returnSB.append(branchesFuture.get());
                returnSB.append(stagedFilesFuture.get());
                returnSB.append(removedFilesFuture.get());
                returnSB.append(modificationsFuture.get());
                returnSB.append(untrackedFilesFuture.get());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                executor.shutdown();
            }

            System.out.println(returnSB.toString());
        }

    /**
     * find 命令
     * 根据message查找commit
     * 并 打印出所有 包含参数message的commit 的ID
     */
    public static void find(String message) {
        checkIfGitletExists();
        StringBuilder returnSB = new StringBuilder();

        // 文件路径--->得到sha1值-->转为commit对象--->得到message-->比较message
        String[] commitDirs = COMMITS_DIR.list();
        for (String commitDir : commitDirs) {
            List<String> commits = plainFilenamesIn(join(COMMITS_DIR, commitDir));
            for (String commit : commits) {
                String ID = commitDir + commit;
                try {
                    // 将sha-1值转为commit对象
                    Commit commitObj = getCommitBySHA(ID);
                    if (commitObj == null) {
                        System.out.println("No commit object found for ID: " + ID);
                        continue;
                    }
                    // 对于每一个commit对象 判断其message是否包含 参数message 的信息
                    if (commitObj.getMessage().contains(message)) {
                        //若想打印commit的详细信息 可调用上面的printCommit方法
                        returnSB.append(ID);
                        returnSB.append("\n");
                    }
                } catch (Exception e) {
                    System.out.println("Error processing commit ID: " + ID);
                    e.printStackTrace();
                }
            }
        }
        if (returnSB.toString().isEmpty()) {
            System.out.println("Found no commit with that message.");
            System.exit(0);
        }
        returnSB.append("\n");
        System.out.println(returnSB.toString());
    }

    /**
     * checkout --[file name]
     *记得参数前面加上--
     * 将 HEAD提交中 的文件版本取出到working dir并覆盖同名文件
     */
    public static void checkoutFileFromHEAD(String fileName){
        checkIfGitletExists();
        Commit headCommit = getHeadCommit();
        checkoutFileFromCommit(headCommit,fileName);
    }

    /**
     * 从一个指定commit中取出文件到working dir  commit--blob（hashmap）--sha1（也是路径）--blob文件--读取内容--写入working dir
     * @param commit
     * @param fileName
     */
    public static void checkoutFileFromCommit(Commit commit,String fileName){
        if(!commit.getBlobs().containsKey(fileName)){
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
        //将指定版本的文件的sha-1值取出来
        byte[] content=readBlobContent(commit,fileName);
        //将其写入working dir
        writeContents(join(CWD,fileName),content);
    }

    public static byte[] readBlobContent(Commit commit,String fileName){
        String ID = commit.fileVersion(fileName);
        //TODO:commit中blob映射和BOBS_DIR文件下的blob文件区别
        //构建存储blob文件的完整路径 然后读取文件内容返回
        //在执行下面这个join之前文件路径已存在  这里用join只是方便构建一个文件对象 从而可以用readContens读取文件内容
        File blobFile=join(join(BLOBS_DIR,ID.substring(0,2)),ID.substring(2));
        return readContents(blobFile);
    }

    /**
     * checkout --[commit id] --[file name]
     * 用户可根据commit id 或者是 缩写的commit id 检出指定版本文件
     */
    public static void checkoutFileFromCommitID(String ID, String fileName) {
        checkIfGitletExists();

        if (ID == null || ID.isEmpty()) {
            System.out.println("Commit ID cannot be null or empty.");
            System.exit(0);
        }
        if (ID.length() < 4) {  // 最小4位
            System.out.println("Commit ID must be at least 4 characters long.");
            System.exit(0);
        }
        if (fileName == null || fileName.isEmpty()) {
            System.out.println("File name cannot be null or empty.");
            System.exit(0);
        }

        if (ID.length() == 40) {
            checkoutFileFromCommit(getCommitBySHA(ID), fileName);
            return;
        }

        File commitPrefix = join(COMMITS_DIR, ID.substring(0, 2));
        if (!commitPrefix.exists()) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }

        List<String> commitIDs = plainFilenamesIn(commitPrefix);
        if (commitIDs == null) {
            System.out.println("Error reading commit directory.");
            System.exit(0);
        }

        List<String> matches = new ArrayList<>();
        for (String commitID : commitIDs) {
            if (commitID.startsWith(ID.substring(2))) {
                matches.add(commitID);
            }
        }

        if (matches.isEmpty()) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        } else if (matches.size() > 1) {
            System.out.println("Multiple commits match the given prefix. Please provide a more specific ID.");
            System.exit(0);
        }

        Commit commitObj = readObject(join(commitPrefix, matches.get(0)), Commit.class);
        checkoutFileFromCommit(commitObj, fileName);
    }

    public static boolean branchExists(String branch){
        File branchFile=join(BRANCHES_DIR,branch);
        return branchFile.exists();
    }

    /**
     * 检查working dir中未跟踪（untracked）的文件是否会被TargetCommit覆盖  如果会则发出警告
     * @param snapShot
     * @param newBlobs
     * @param targetCommit
     */
    public static void checkUntrackedOverwritten(List<String> snapShot, HashMap<String, String> newBlobs, Commit targetCommit) {
        if (snapShot == null || newBlobs == null || targetCommit == null) {
            throw new IllegalArgumentException("Input parameters cannot be null");
        }
        for (String fileName : snapShot) {
            File file = join(CWD, fileName);
            if (!file.isFile()) continue; // 忽略目录
            if (!newBlobs.containsKey(fileName) && targetCommit.tracks(fileName)) {
                throw new IllegalStateException(
                        "Untracked file '" + fileName + "' would be overwritten by checkout. Delete it, or add and commit it first."
                );
            }
        }
    }

    //根据blob文件的id（sha-1）读取
    public static byte[] getBlobContent(String blobID){
        File blob=join(BLOBS_DIR,blobID.substring(0,2),blobID.substring(2));
        return readContents(blob);
    }
    private static String readBlobContentAsString(Commit commit,String fileName){
        if(!commit.tracks(fileName)){
            return "";
        }
        String ID = commit.fileVersion(fileName);
        File file=join(join(BLOBS_DIR,ID.substring(0,2)),ID.substring(2));
        return readContentsAsString(file);
    }

    public static void checkoutCommit(Commit targetCommit){
        //获取newBlobs 和 snapshot
        Commit headCommit = getHeadCommit();
        Index changes=Index.getStagingArea();
        HashMap<String, String> newBlobs = getNewBlobs(headCommit, changes);
        //这里用到的的snapshot区别于下面的snapshot，这里只需要文件名，而下方的是blob对象
        // HashMap<String, String> snapShot = takeSnapshot();
        List<String> snapShot=plainFilenamesIn(CWD);

        //检查未跟踪的文件是否会被覆盖
        checkUntrackedOverwritten(snapShot,newBlobs,targetCommit);

        //删除不再被targetCommit追踪的文件
        for(String fileName : snapShot){
            if(headCommit.tracks(fileName) && !targetCommit.tracks(fileName) ){
                restrictedDelete(fileName);
            }
        }
        //将targetCommit中的文件写入到工作目录
        for(Map.Entry<String,String> entry : targetCommit.getBlobs().entrySet()){
            File blobFile=join(CWD,entry.getKey());
            String blobID = entry.getValue();
            writeContents(blobFile,getBlobContent(blobID));
        }
        //清空并保存暂存区
        changes.clear();
        changes.save();
    }

    /**
     * checkout [branch name]
     * precheck-->获取目标分支的最新commit-->切换到targetCommit-->更新HEAD
     */
    public static void checkoutBranch(String branch){
        /** Precheck. */
        checkIfGitletExists();
        if(!branchExists(branch)){
            System.out.println("No such branch exists.");
            System.exit(0);
        }
        String curBranch=readContentsAsString(HEAD);
        if(curBranch.equals(branch)){
            System.out.println("No need to checkout the current branch.");
            System.exit(0);
        }
        //checkout
        Commit targetCommit=getCommitBySHA(getHeadCommitID(branch));
        checkoutCommit(targetCommit);
        //update HEAD
        writeContents(HEAD,branch);
    }

    /**
     * branch [branch name] 命令
     * 该命令只包含创建分支
     */
    public static void newBranch(String branchName){
        checkIfGitletExists();
        File branch=join(BRANCHES_DIR,branchName);
        if(branch.exists()){
            System.out.println("A branch with that name already exists.");
            System.exit(0);
        }
        //新分支指向当前分支的最新提交
        writeContents(branch,getHeadCommitID(readContentsAsString(HEAD)));
    }

    /**
     * rm branch [branch name] 命令
     * TODO : 1. 删除分支前检查是否合并，警告合并后再删除
     * TODO： 2. 删除分支后存在悬空commit 可通过实现垃圾回收  参考git中设计
     * 只需要删除该分支的指针 不需要删除这个分支下的commit
     */
    public static void removeBranch(String branchName){
        checkIfGitletExists();
        File branch=join(BRANCHES_DIR,branchName);
        if(!branch.exists()){
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        if(readContentsAsString(HEAD).equals(branchName)){
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        }
        branch.delete();
    }

    /**
     * reset [commit id] 命令
     */
    public static void reset(String commitID){
        checkIfGitletExists();
        //以传入的commitID构建一个文件路径 来判断这个commit是否存在
        File commit=join(join(COMMITS_DIR,commitID.substring(0,2)), commitID.substring(2));
        if(!commit.exists()){
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        //复用checkout命令中用到的checkoutCommit方法
        checkoutCommit(getCommitBySHA(commitID));
        String curBranch=readContentsAsString(HEAD);
        writeContents(join(BRANCHES_DIR,curBranch),commitID);
    }
    /**
     * merge [branch name] 命令
     * 在给定分支中修改过  在当前分支未修改 --->改为给定分支【branch name】中的版本
     *
     */
    public static void merge(String branchName){
        /** Precheck */
        File branch=join(BRANCHES_DIR,branchName);
        if(!branch.exists()){
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        String curBranch=readContentsAsString(HEAD);
        if(branchName.equals(curBranch)){
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }
        Index changes=Index.getStagingArea();
        if(!changes.isEmpty()){
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }

        /** 确定 拆分点
         * 这里先判断两种特殊情况：
         * 1.given branch 落后于 cur branch-->given branch的headCommit是cur branch的祖先--->不需要merge given branch 直接退出s
         * 2.cur branch 落后于 given branch-->说明当前分支的headCommit时given branch 的祖先--->快速合并--->将cur branch的head指向given branch的head
         */
        String splitPointID=getSplitPointID(branchName); //拆分点
        String curCommitID= getHeadCommitID(curBranch);  //cur branch的head
        String mergedCommitID=getHeadCommitID(branchName);  //given branch的head
        if(mergedCommitID.equals(splitPointID)) {//上述情况1
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }
        if(splitPointID.equals(curCommitID)){//上述情况2 :执行快进合并
            //更新工作目录：由于working dir中是cur branch的head commit内容，故先将given branch的head commit内容checkout到working dir中
            checkoutCommit(getCommitBySHA(mergedCommitID));
            //更新head: 将cur branch的head指向given branch的head
            writeContents(join(BRANCHES_DIR,curBranch),mergedCommitID);
            System.out.println("Current branch fast-forwarded.");
            return;
        }

        /** 一般情况 */
        Commit splitPoint=getCommitBySHA(splitPointID);
        Commit curCommit=getCommitBySHA(curCommitID);
        Commit mergedCommit=getCommitBySHA(mergedCommitID);

        //检查是否有未跟踪文件 有则报错
        checkUntrackedOverwritten(plainFilenamesIn(CWD),curCommit.getBlobs(),mergedCommit);

        //处理合并分支中修改或添加的文件
        HashSet<String> modifiedOrAddInMerge=modifiedOrAddInMergedBranch(splitPoint,curCommit,mergedCommit);
        for(String fileName : modifiedOrAddInMerge){
            //将这些文件检出到working dir
            checkoutFileFromCommit(mergedCommit,fileName);
            //添加到staging area
            changes.staged.put(fileName,mergedCommit.fileVersion(fileName));
        }
        //处理合并分支中删除的文件
        HashSet<String> deletedInMerge=deletedInMergedBranch(splitPoint,curCommit,mergedCommit);
        for(String fileName : deletedInMerge){
            //将这些人间存到staging area的removed中 并将他们从working dir中删除
            changes.removed.put(fileName,curCommit.fileVersion(fileName));
            restrictedDelete(join(CWD,fileName));
        }
        //处理两个分支中同时修改的文件
        HashSet<String> bothModified = bothModified(splitPoint,curCommit,mergedCommit);
        if(!bothModified.isEmpty()) {
            System.out.println("both Modified的文件："+bothModified);
            for(String fileName : bothModified){
                //将冲突信息写入文件
                writeConflict(fileName,branchName,curCommit,mergedCommit);
                //将cur branch的冲突文件添加到暂存区  便于解决冲突后重新提交
                changes.staged.put(fileName,sha1(readContents(join(CWD,fileName))));
            }
            //将给定分支的 HEAD commit ID 写入临时文件（例如 MERGE_HEAD）。
            writeContents(join(GITLET_DIR, "MERGE_HEAD"), mergedCommitID);
            changes.save();
            System.out.println("Encountered a merge conflict.");
            return;
        }

        //创建新的commit
        Commit mergeCommit=new Commit(
                new Date(),
                "Merged " + branchName + " into " + curBranch + ".",
                new String[] {curCommitID,mergedCommitID},
                getNewBlobs(curCommit,changes)
        );
        String newID=sha1(mergeCommit.toString());
        mergeCommit.save(newID);
        //更新HEAD
        writeContents(join(BRANCHES_DIR,curBranch),newID);
        //清理和保存
        changes.clear();
        changes.save();
    }

    /** 得到 given branch 和cur branch 的拆分点的SHA-1值
     * 1.遍历cur branch的所有commit，将所有的ID添加到一个set集合中
     * 2.遍历given branch的commit ，判断set集合中是否含有该commit，若有就返回
     */
    public static String getSplitPointID(String branchName){
        //history用来存储cur branch中所有commit的ID
        HashSet history=new HashSet<>();
        String curID=getHeadCommitID(readContentsAsString(HEAD));
        history.add(curID);
        //队列q用于遍历commit tree
        Queue<String> q=new LinkedList<>();
        q.add(curID);
        /* BFS  */
        while(!q.isEmpty()){
            curID=q.poll();
            Commit curCommit=getCommitBySHA(curID);
            for(String parentID : curCommit.getParents()){
                if(parentID!=null){
                    history.add(parentID);
                    q.add(parentID);
                }
            }
        }
        System.out.println("打印set集合："+history);
        /*遍历given branch  找到拆分点*/
        curID=getHeadCommitID(branchName);
        q.clear();
        q.add(curID);
        while(!q.isEmpty()){
            curID=q.poll();
            if(history.contains(curID)){
                System.out.println("拆分点："+curID);
                return curID;
            }
            System.out.println("检查点1："+curID);
            //TODO： merge操作时此处空指针异常
            Commit curCommit=getCommitBySHA(curID);
            for(String parentID : curCommit.getParents()){
                if(parentID!=null){
                    System.out.println("检查点2："+parentID);
                    q.add(parentID);
                }
            }
        }
        System.out.println("返回拆分点："+curID);
        return curID;
    }

    private static HashSet<String> modifiedOrAddInMergedBranch(Commit splitPoint,Commit curCommit,Commit mergedCommit){
        HashSet<String> modifiedOrAddInMerge=new HashSet<>();
        for(Map.Entry<String,String> entry : mergedCommit.getBlobs().entrySet() ){
            //只在合并分支中修改的文件
            if(splitPoint.tracks(entry.getKey()) &&
                !splitPoint.fileVersion(entry.getKey()).equals(entry.getValue()) &&
                curCommit.tracks(entry.getKey()) &&
                curCommit.fileVersion(entry.getKey()).equals(splitPoint.fileVersion(entry.getKey()))){
                    modifiedOrAddInMerge.add(entry.getKey());
            } else if (!splitPoint.tracks(entry.getKey()) && !curCommit.tracks(entry.getKey())) {
                //新增的文件
                    modifiedOrAddInMerge.add(entry.getKey());
            }
        }
        return modifiedOrAddInMerge;
    }

    private static HashSet<String> deletedInMergedBranch(Commit splitPoint,Commit curCommit,Commit mergedCommit){
        HashSet<String> deletedInMerge=new HashSet<>();
        for(Map.Entry<String,String> entry : curCommit.getBlobs().entrySet()){
            if(splitPoint.tracks(entry.getKey()) &&
            !mergedCommit.tracks(entry.getKey()) &&
            curCommit.fileVersion(entry.getKey()).equals(splitPoint.fileVersion(entry.getKey()))){
                deletedInMerge.add(entry.getKey());
            }
        }
        return deletedInMerge;
    }

    /** the SITUATION where merge and cur branch -MODIFIED IN Different Ways---下面4个if语句分别对应1234
     *  1. mer and cur 都 新增 但 内容不一样
     *  2. mer and cur 都 修改
     *  3. cur 修改 mer 删除
     *  4. cur 删除 mer 修改
     *  */
    private static HashSet<String> bothModified(Commit splitPoint, Commit curCommit, Commit mergedCommit) {
        HashSet<String> bothModified = new HashSet<>();

        // 检查 curCommit 中的文件
        if (curCommit.getBlobs() != null) {
            for (Map.Entry<String, String> entry : curCommit.getBlobs().entrySet()) {
                String fileName = entry.getKey();
                String curVersion = entry.getValue();

                // 情况 1：双方新增，内容不同
                if (!splitPoint.tracks(fileName) &&
                        mergedCommit.tracks(fileName) &&
                        !curVersion.equals(mergedCommit.fileVersion(fileName))) {
                    bothModified.add(fileName);
                }
                // 情况 2：双方修改，内容不同
                else if (splitPoint.tracks(fileName) &&
                        mergedCommit.tracks(fileName) &&
                        !splitPoint.fileVersion(fileName).equals(curVersion) &&
                        !splitPoint.fileVersion(fileName).equals(mergedCommit.fileVersion(fileName)) &&
                        !curVersion.equals(mergedCommit.fileVersion(fileName))) {
                    bothModified.add(fileName);
                }
                // 情况 3：当前分支修改，给定分支删除
                else if (splitPoint.tracks(fileName) &&
                        !splitPoint.fileVersion(fileName).equals(curVersion) &&
                        !mergedCommit.tracks(fileName)) {
                    bothModified.add(fileName);
                }
            }
        }
        // 检查 mergedCommit 中的文件
        if (mergedCommit.getBlobs() != null) {
            for (Map.Entry<String, String> entry : mergedCommit.getBlobs().entrySet()) {
                String fileName = entry.getKey();
                String mergedVersion = entry.getValue();

                // 情况 4：当前分支删除，给定分支修改
                if (splitPoint.tracks(fileName) &&
                        !splitPoint.fileVersion(fileName).equals(mergedVersion) &&
                        !curCommit.tracks(fileName)) {
                    bothModified.add(fileName);
                }
            }
        }
        return bothModified;
    }

    private static void writeConflict(String fileName, String branchName, Commit curCommit, Commit mergedCommit) {
        StringBuilder returnSB = new StringBuilder();
        File conflictFile = join(CWD, fileName);
        returnSB.append("<<<<<<< HEAD\n");
        String curContent = curCommit.tracks(fileName) ? readBlobContentAsString(curCommit, fileName) : "(file deleted)";
        returnSB.append(curContent);
        returnSB.append("\n=======\n");
        String mergedContent = mergedCommit.tracks(fileName) ? readBlobContentAsString(mergedCommit, fileName) : "(file deleted)";
        returnSB.append(mergedContent);
        returnSB.append("\n>>>>>>> " + branchName + "\n");
        writeContents(conflictFile, returnSB.toString());
    }
}
