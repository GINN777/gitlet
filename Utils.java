package gitlet;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;


/** Assorted utilities.
 *
 * Give this file a good read as it provides several useful utility functions
 * to save you some time.
 *
 *  @author P. N. Hilfinger
 */
class Utils {

    /** The length of a complete SHA-1 UID as a hexadecimal numeral. */
    static final int UID_LENGTH = 40;

    /* SHA-1 HASH VALUES. */

    /** 返回VALS的连接的SHA-1哈希值，VALS可以是任何混合的字节数组和字符串。 */
    static String sha1(Object... vals) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (Object val : vals) {
                if (val instanceof byte[]) {
                    //如果是字节数组，则直接使用这些字节
                    md.update((byte[]) val);
                } else if (val instanceof String) {
                    //如果参数是字符串，则会先将字符串转换为UTF-8编码的字节，再用于哈希
                    md.update(((String) val).getBytes(StandardCharsets.UTF_8));
                } else {
                    throw new IllegalArgumentException("improper type to sha1");
                }
            }
            Formatter result = new Formatter();
            for (byte b : md.digest()) {
                result.format("%02x", b);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException excp) {
            throw new IllegalArgumentException("System does not support SHA-1");
        }
    }

    /** 返回VALS中字符串连接的SHA-1哈希值。
     * 这个方法接收List<Object>类型的参数  通过将这个列表转为可变参数数组，--然后调用上面的sha1方法来计算SHA-1哈希值 */
    static String sha1(List<Object> vals) {
        return sha1(vals.toArray(new Object[vals.size()]));
    }

    /* FILE DELETION */

    /** Deletes FILE if it exists and is not a directory.  Returns true
     *  if FILE was deleted, and false otherwise.  Refuses to delete FILE
     *  and throws IllegalArgumentException unless the directory designated by
     *  FILE also contains a directory named .gitlet.
     *
     *  如果文件存在且不是目录，则删除该文件。如果文件被删除，则返回true，否则返回false。
     *  除非目录中包含名为.gitlet的目录，否则拒绝删除文件并抛出IllegalArgumentException。*/
    static boolean restrictedDelete(File file) {
        //判断父目录中是否包含.gitlet 如果否，则说明不是工作目录，抛出异常
        if (!(new File(file.getParentFile(), ".gitlet")).isDirectory()) {
            throw new IllegalArgumentException("not .gitlet working directory");
        }
        if (!file.isDirectory()) {//并且不能删除目录
            return file.delete();
        } else {
            return false;
        }
    }

    /** Deletes the file named FILE if it exists and is not a directory.
     *  Returns true if FILE was deleted, and false otherwise.  Refuses
     *  to delete FILE and throws IllegalArgumentException unless the
     *  directory designated by FILE also contains a directory named .gitlet.
     *参数为 文件路径
     *  如果文件存在且不是目录，则删除该文件。如果文件被删除，则返回true，否则返回false。
     *  除非目录中包含名为.gitlet的目录，否则拒绝删除文件并抛出IllegalArgumentException。*/
    static boolean restrictedDelete(String file) {
        return restrictedDelete(new File(file));
    }

    /* READING AND WRITING FILE CONTENTS */

    /** Return the entire contents of FILE as a byte array.
     * FILE must be a normal file.  Throws IllegalArgumentException
     *  in case of problems.
     *
     * 将文件的全部内容  以  一个字节数组 返回 */
    static byte[] readContents(File file) {
        if (!file.isFile()) {
            throw new IllegalArgumentException("must be a normal file");
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException excp) {
            throw new IllegalArgumentException(excp.getMessage());
        }
    }

    /** Return the entire contents of FILE as a String.  FILE must
     *  be a normal file.  Throws IllegalArgumentException
     *  in case of problems.
     *
     *  将文件内容 以 字符串 返回 */
    static String readContentsAsString(File file) {
        return new String(readContents(file), StandardCharsets.UTF_8);
    }

    /** Write the result of concatenating the bytes in CONTENTS to FILE,
     *  creating or overwriting it as needed.  Each object in CONTENTS may be
     *  either a String or a byte array.  Throws IllegalArgumentException
     *  in case of problems. */
    /**
     * 将CONTENTS的字节连接的结果写入文件，根据需要创建或覆盖文件。
     * CONTENTS中的每个对象可以是字符串或字节数组。如果出现问题则抛出IllegalArgumentException。
     */
    static void writeContents(File file, Object... contents) {
        try {
            if (file.isDirectory()) {
                throw new IllegalArgumentException("cannot overwrite directory");
            }
            BufferedOutputStream str =
                new BufferedOutputStream(Files.newOutputStream(file.toPath()));
            for (Object obj : contents) {
                if (obj instanceof byte[]) {
                    str.write((byte[]) obj);
                } else {
                    str.write(((String) obj).getBytes(StandardCharsets.UTF_8));
                }
            }
            str.close();
        } catch (IOException | ClassCastException excp) {
            throw new IllegalArgumentException(excp.getMessage());
        }
    }

    /** Return an object of type T read from FILE, casting it to EXPECTEDCLASS.
     *  Throws IllegalArgumentException in case of problems.  */
    /** 从FILE中读取一个对象，将其转换为 自己期望的 类型。
     * 如果出现问题则抛出IllegalArgumentException。 */
    static <T extends Serializable> T readObject(File file,
                                                 Class<T> expectedClass) {
        try {
            //创建FileInputStream对象 读取文件内容
            //将 FileInputStream 传递给 ObjectInputStream 的构造函数，创建一个 ObjectInputStream 对象 in。
            // ObjectInputStream 能够读取序列化的对象。
            ObjectInputStream in =
                new ObjectInputStream(new FileInputStream(file));
            //调用redObject()方法 读取序列化对象  AKA：将 文件中的 字节流--反序列化成--对象（所以下面readObject()方法返回的类型为对象
            //将读取的对象转换为期望的类型，并返回结果。
            T result = expectedClass.cast(in.readObject());
            in.close();
            //返回读取并转换的对象
            return result;
        } catch (IOException | ClassCastException
                 | ClassNotFoundException excp) {
            throw new IllegalArgumentException(excp.getMessage());
        }
    }

    /** Write OBJ to FILE. */
    /** 将OBJ写入FILE。
     * 序列化 并将其写入文件 */
    static void writeObject(File file, Serializable obj) {
        //serialize方法将obj对象转换为字节数组
        writeContents(file, serialize(obj));
    }

    /* DIRECTORIES */

    /** Filter out all but plain files. */
    /** 过滤掉所有非普通文件。 */
    private static final FilenameFilter PLAIN_FILES =
        new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isFile();
            }
        };

    /** Returns a list of the names of all plain files in the directory DIR, in
     *  lexicographic order as Java Strings.  Returns null if DIR does
     *  not denote a directory. */
    /** 返回DIR目录中所有普通文件的名称列表，按字典顺序排列为Java字符串。如果DIR不表示目录，则返回null。 */
    static List<String> plainFilenamesIn(File dir) {
        String[] files = dir.list(PLAIN_FILES);
        if (files == null) {
            return null;
        } else {
            Arrays.sort(files);
            return Arrays.asList(files);
        }
    }

    /** Returns a list of the names of all plain files in the directory DIR, in
     *  lexicographic order as Java Strings.  Returns null if DIR does
     *  not denote a directory. */
    /** 返回DIR目录中所有普通文件的名称列表，按字典顺序排列为Java字符串。
     * 如果DIR不表示目录，则返回null。 */
    static List<String> plainFilenamesIn(String dir) {
        return plainFilenamesIn(new File(dir));
    }

    /* OTHER FILE UTILITIES */

    /** Return the concatentation of FIRST and OTHERS into a File designator,
     *  将FIRST和OTHERS连接成一个文件指示器
     *  analogous to the {@link java.nio.file.Paths.#get(String, String[])}
     *  method. */
    static File join(String first, String... others) {
        return Paths.get(first, others).toFile();
    }

    /** Return the concatentation of FIRST and OTHERS into a File designator,
     *  analogous to the *
     *  {@link java.nio.file.Paths.#get(String, String[])}
     *  method. */
    static File join(File first, String... others) {
        return Paths.get(first.getPath(), others).toFile();
    }


    /* SERIALIZATION UTILITIES */

    /** Returns a byte array containing the serialized contents of OBJ. */
    /** 返回一个包含OBJ序列化内容的字节数组。
     * serialize 方法通过使用 ObjectOutputStream 将对象转换为字节流，并使用 ByteArrayOutputStream 捕获这个字节流，最终将字节流转换为字节数组。*/
    /**这个方法是对象持久化的关键步骤，允许对象的状态被保存到文件、数据库或通过网络传输。*/
    static byte[] serialize(Serializable obj) {
        try {
            //创建一个 ByteArrayOutputStream 对象 stream，它是一个内存中的输出流，用于存储序列化后的数据。
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            //ObjectOutputStream 是用于序列化对象的输出流。
            ObjectOutputStream objectStream = new ObjectOutputStream(stream);
            //调用 objectStream 的 writeObject 方法，将传入的对象 obj 序列化并写入到 objectStream 中。
            // 这个方法会将对象的状态信息转换为字节流，并存储在 ByteArrayOutputStream 中。
            objectStream.writeObject(obj);
            objectStream.close();
            //调用 ByteArrayOutputStream 的 toByteArray 方法，将存储在流中的字节数据转换成一个字节数组，并返回这个数组。
            // 这个字节数组就是对象的序列化形式。
            return stream.toByteArray();
        } catch (IOException excp) {
            throw error("Internal error serializing commit.");
        }
    }



    /* MESSAGES AND ERROR REPORTING */

    /** Return a GitletException whose message is composed from MSG and ARGS as
     *  for the String.format method. */
    static GitletException error(String msg, Object... args) {
        return new GitletException(String.format(msg, args));
    }

    /** Print a message composed from MSG and ARGS as for the String.format
     *  method, followed by a newline. */
    static void message(String msg, Object... args) {
        System.out.printf(msg, args);
        System.out.println();
    }
}
