package org.horaapps.leafpic.model;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.lang.annotations.NotNull;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import org.horaapps.leafpic.R;
import org.horaapps.leafpic.model.base.MediaDetailsMap;
import org.horaapps.leafpic.util.MediaSignature;
import org.horaapps.leafpic.util.StringUtils;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by dnld on 26/04/16.
 */
public class Media implements Parcelable, Serializable {

    private String path = null;
    private long dateModified = -1;
    private String mimeType = "unknown";
    private int orientation = 0;

    private String uriString = null;

    private long size = -1;
    private boolean selected = false;
    private MetaDataItem metadata;

    public Media() { }

    public Media(String path, long dateModified) {
        this.path = path;
        this.dateModified = dateModified;
        this.mimeType = StringUtils.getMimeType(path);
    }

    public Media(File file) {
        this(file.getPath(), file.lastModified());
        this.size = file.length();
        this.mimeType = StringUtils.getMimeType(path);
    }

    public Media(String path) {
        this(path, -1);
    }

    public Media(Context context, Uri mediaUri) {
        this.uriString = mediaUri.toString();
        this.path = null;
        this.mimeType = context.getContentResolver().getType(getUri());
    }

    public Media(@NotNull Cursor cur) {
        this.path = cur.getString(0);
        this.dateModified = cur.getLong(1);
        this.mimeType = cur.getString(2);
        this.size = cur.getLong(3);
        this.orientation = cur.getInt(4);
    }

    public void setUri(String uriString) {
        this.uriString = uriString;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean isSelected() {
        return selected;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isGif() { return mimeType.endsWith("gif"); }

    public boolean isImage() { return mimeType.startsWith("image"); }

    public boolean isVideo() { return mimeType.startsWith("video"); }

    public Uri getUri() {
        return uriString != null ? Uri.parse(uriString) : Uri.fromFile(new File(path));
    }

    private InputStream getInputStream(ContentResolver contentResolver) throws Exception {
        return contentResolver.openInputStream(getUri());
    }

    public String getName() {
        return StringUtils.getPhotoNameByPath(path);
    }

    public long getSize() {
        return size;
    }

    public String getPath() {
        return path;
    }

    public Long getDateModified() {
        return dateModified;
    }


    public Bitmap getBitmap(){
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        Bitmap bitmap = BitmapFactory.decodeFile(path,bmOptions);
        bitmap = Bitmap.createScaledBitmap(bitmap,bitmap.getWidth(),bitmap.getHeight(),true);
        return bitmap;
    }

    public MediaSignature getSignature() {
        return new MediaSignature(this);
    }

    //<editor-fold desc="Exif & More">
    public GeoLocation getGeoLocation()  {
        return metadata != null ? metadata.getLocation() : null;
    }

    public MediaDetailsMap<String, String> getAllDetails() {
        MediaDetailsMap<String, String> data = new MediaDetailsMap<String, String>();
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new File(path));
            for(Directory directory : metadata.getDirectories()) {

                for(Tag tag : directory.getTags()) {
                    data.put(tag.getTagName(), directory.getObject(tag.getTagType())+"");
                }
            }
        } catch (ImageProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    public MediaDetailsMap<String, String> getMainDetails(Context context){
        MediaDetailsMap<String, String> details = new MediaDetailsMap<String, String>();
        details.put(context.getString(R.string.path), path != null ? path : getUri().getEncodedPath());
        details.put(context.getString(R.string.type), getMimeType());
        if(size != -1)
            details.put(context.getString(R.string.size), StringUtils.humanReadableByteCount(size, true));
        // TODO should i add this always?
        details.put(context.getString(R.string.orientation), getOrientation() + "");
        try {
            metadata = MetaDataItem.getMetadata(getInputStream(context.getContentResolver()));
            details.put(context.getString(R.string.resolution), metadata.getResolution());
            details.put(context.getString(R.string.date), SimpleDateFormat.getDateTimeInstance().format(new Date(dateModified)));
            Date dateOriginal = metadata.getDateOriginal();
            if (dateOriginal != null )
                details.put(context.getString(R.string.date_taken), SimpleDateFormat.getDateTimeInstance().format(dateOriginal));

            String tmp;
            if ((tmp = metadata.getCameraInfo()) != null)
                details.put(context.getString(R.string.camera), tmp);
            if ((tmp = metadata.getExifInfo()) != null)
                details.put(context.getString(R.string.exif), tmp);
            GeoLocation location;
            if ((location = metadata.getLocation()) != null)
                details.put(context.getString(R.string.location), location.toDMSString());
// [landonb]
            try {
                Metadata metadata = ImageMetadataReader.readMetadata(new File(path));
//
//                print(metadata, details);
            } catch (ImageProcessingException e) {
                details.put("ImageProcessingException", e.getMessage());
            } catch (IOException e) {
                details.put("IOException", e.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return details;
    }

    private static void print(Metadata metadata, MediaDetailsMap<String, String> details)
    {
        System.out.println("-------------------------------------");
        // Iterate over the data and print to System.out
        // A Metadata object contains multiple Directory objects
        for (Directory directory : metadata.getDirectories()) {
            // Each Directory stores values in Tag objects
            for (Tag tag : directory.getTags()) {
                System.out.println(tag);
                details.put(tag.getTagName(), directory.getObject(tag.getTagType())+"");
            }
            // Each Directory may also contain error messages
            if (directory.hasErrors()) {
                for (String error : directory.getErrors()) {
                    System.err.println("ERROR: " + error);
                }
            }
        }
    }

    public boolean setOrientation(final int orientation) {
        this.orientation = orientation;
        // TODO: 28/08/16  find a better way
        // TODO update also content provider
        new Thread(new Runnable() {
            public void run() {
                int exifOrientation = -1;
                try {
                    ExifInterface  exif = new ExifInterface(path);
                    switch (orientation) {
                        case 90: exifOrientation = ExifInterface.ORIENTATION_ROTATE_90; break;
                        case 180: exifOrientation = ExifInterface.ORIENTATION_ROTATE_180; break;
                        case 270: exifOrientation = ExifInterface.ORIENTATION_ROTATE_270; break;
                        case 0: exifOrientation = ExifInterface.ORIENTATION_NORMAL; break;
                    }
                    if (exifOrientation != -1) {
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(exifOrientation));
                        exif.saveAttributes();
                    }
                }
                catch (IOException ignored) {  }
            }
        }).start();
        return true;
    }

    private long getDateTaken() {
        // TODO: 16/08/16 improved
        Date dateOriginal = metadata.getDateOriginal();
        if (dateOriginal != null) return metadata.getDateOriginal().getTime();
        return -1;
    }

    public boolean fixDate(){
        long newDate = getDateTaken();
        if (newDate != -1){
            File f = new File(path);
            if (f.setLastModified(newDate)) {
                dateModified = newDate;
                return true;
            }
        }
        return false;
    }
    //</editor-fold>

    public File getFile() {
        if (path != null) {
            File file = new File(path);
            if (file.exists()) return file;
        }
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }


    //TODO add orientation
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.path);
        dest.writeLong(this.dateModified);
        dest.writeString(this.mimeType);
        dest.writeLong(this.size);
        dest.writeByte(this.selected ? (byte) 1 : (byte) 0);
    }

    protected Media(Parcel in) {
        this.path = in.readString();
        this.dateModified = in.readLong();
        this.mimeType = in.readString();
        this.size = in.readLong();
        this.selected = in.readByte() != 0;
    }

    public static final Creator<Media> CREATOR = new Creator<Media>() {
        @Override
        public Media createFromParcel(Parcel source) {
            return new Media(source);
        }

        @Override
        public Media[] newArray(int size) {
            return new Media[size];
        }
    };

    public int getOrientation() {
        return orientation;
    }

    //<editor-fold desc="Thumbnail Tests">
    @TestOnly public byte[] getThumbnail() {

        ExifInterface exif;
        try {
            exif = new ExifInterface(path);
        } catch (IOException e) {
            return null;
        }
        if (exif.hasThumbnail())
            return exif.getThumbnail();
        return null;

        // NOTE: ExifInterface is faster than metadata-extractor to getValue the thumbnail data
        /*try {
            Metadata metadata = ImageMetadataReader.readMetadata(new File(getMediaPath()));
            ExifThumbnailDirectory thumbnailDirectory = metadata.getFirstDirectoryOfType(ExifThumbnailDirectory.class);
            if (thumbnailDirectory.hasThumbnailData())
                return thumbnailDirectory.getThumbnailData();
        } catch (Exception e) { return null; }*/
    }

    @TestOnly public String getThumbnail(Context context) {
        /*Cursor cursor = MediaStore.Images.Thumbnails.queryMiniThumbnail(
                context.getContentResolver(), id,
                MediaStore.Images.Thumbnails.MINI_KIND,
                new String[]{ MediaStore.Images.Thumbnails.DATA } );
        if(cursor.moveToFirst())
            return cursor.getString(cursor.getColumnIndex(MediaStore.Images.Thumbnails.DATA));
        return null;*/
        return null;
    }
    //</editor-fold>
}
