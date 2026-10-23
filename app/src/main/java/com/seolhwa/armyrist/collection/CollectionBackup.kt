package com.seolhwa.armyrist.collection

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Portable folder metadata and image bytes; never trust paths supplied by a backup. */
internal object CollectionBackup {
    const val PREFS="armyrist_common_collections"
    const val KEY="folders_v1"
    private const val MAX_IMAGE_BYTES=6*1024*1024
    fun export(context: Context): JSONObject {
        val raw=context.getSharedPreferences(PREFS,0).getString(KEY,null)
        val root=raw?.let(::JSONObject) ?: JSONObject().put("schemaVersion",1).put("folders",JSONArray())
        val dir=File(context.filesDir,"armyrist_collection_covers").canonicalFile
        val folders=root.getJSONArray("folders")
        for(i in 0 until folders.length()){
            val f=folders.getJSONObject(i);val path=f.optString("coverImagePath")
            if(path.isNotBlank()){
                val file=File(path).canonicalFile
                require(file.path.startsWith(dir.path+File.separator) && file.isFile){"폴더 커버 이미지를 읽을 수 없습니다."}
                require(file.length()<=MAX_IMAGE_BYTES){"폴더 커버 이미지가 너무 큽니다."}
                f.put("coverBase64",Base64.encodeToString(file.readBytes(),Base64.NO_WRAP))
            }
            f.remove("coverImagePath")
        }
        validate(root);return root
    }
    fun validate(root: JSONObject){
        require(root.getInt("schemaVersion")==1)
        val folders=root.getJSONArray("folders");val ids=mutableSetOf<String>();val memberKeys=mutableSetOf<String>()
        for(i in 0 until folders.length()){
            val f=folders.getJSONObject(i);require(f.getString("id").isNotBlank()&&ids.add(f.getString("id")))
            val tool=CollectionToolType.valueOf(f.getString("toolType"));require(f.getString("name").isNotBlank());require(f.getInt("order")>=0)
            val members=f.getJSONArray("memberIds")
            for(j in 0 until members.length()){val id=members.getString(j);require(id.isNotBlank()&&memberKeys.add("$tool:$id"))}
            if(f.has("coverBase64")){
                val encoded=f.getString("coverBase64");require(encoded.length<=MAX_IMAGE_BYTES*4/3+8)
                val bytes=Base64.decode(encoded,Base64.DEFAULT);require(bytes.size<=MAX_IMAGE_BYTES)
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth>0&&bounds.outHeight>0)
            }
        }
    }
    fun restore(context:Context,portable:JSONObject):Boolean {
        validate(portable)
        val root=JSONObject(portable.toString());val folders=root.getJSONArray("folders")
        val dir=File(context.filesDir,"armyrist_collection_covers").apply{mkdirs()};val created=mutableListOf<File>()
        return try {
            for(i in 0 until folders.length()){
                val f=folders.getJSONObject(i);f.remove("coverImagePath")
                val encoded=f.optString("coverBase64");f.remove("coverBase64")
                val path=if(encoded.isBlank())"" else {
                    val file=File(dir,"restored-${UUID.randomUUID()}.img");created+=file;file.writeBytes(Base64.decode(encoded,Base64.DEFAULT));file.absolutePath
                }
                f.put("coverImagePath",path)
            }
            val ok=context.getSharedPreferences(PREFS,0).edit().putString(KEY,root.toString()).commit()
            if(!ok)created.forEach{it.delete()};ok
        }catch(e:Exception){created.forEach{it.delete()};false}
    }
}
