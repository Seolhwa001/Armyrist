package com.seolhwa.armyrist.calendar
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.collection.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import org.json.JSONObject
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=ArmyristApplication::class)
class FolderBackupUiTest {
    private fun <T> success(result:PortableResult<T>):T {assertTrue(result.toString(),result is PortableResult.Success);return (result as PortableResult.Success).value}
    @Test fun fullBackupRestoresFolderMembershipOrderAndImageBytes(){
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>();val plans=app.dateAwareTimePlanRepository
        val p1=plans.createPlan()!!;val p2=plans.createPlan()!!;val repo=CommonCollectionRepository(app)
        val folder=repo.createFolder(CollectionToolType.TIME_PLAN,"당직 계획",listOf(p2.id,p1.id))!!
        val source=File(app.cacheDir,"folder-cover.png");source.outputStream().use{Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}.compress(Bitmap.CompressFormat.PNG,100,it)}
        assertTrue(repo.importCoverImage(folder.id,Uri.fromFile(source)));val original=File(repo.getFolder(folder.id)!!.coverImagePath!!).readBytes()
        val backup=success(ArmyristPortableDataManager.validateBackup(success(ArmyristPortableDataManager.createFullBackup(app,null)),null))
        assertEquals(1,backup.summary.folders)
        assertTrue(repo.deleteFolder(folder.id));repo.createFolder(CollectionToolType.TIME_PLAN,"다른 폴더",listOf(p1.id))
        success(ArmyristPortableDataManager.restoreFullBackup(app,backup))
        val restored=CommonCollectionRepository(app).folders(CollectionToolType.TIME_PLAN).single()
        assertEquals(folder.id,restored.id);assertEquals("당직 계획",restored.name);assertEquals(listOf(p2.id,p1.id),restored.memberIds);assertEquals(0,restored.order)
        assertArrayEquals(original,File(restored.coverImagePath!!).readBytes())
        assertNotEquals(source.absolutePath,restored.coverImagePath)
    }
    @Test fun malformedFoldersAreRejectedBeforeExistingDataChanges(){
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>();val plan=app.dateAwareTimePlanRepository.createPlan()!!
        val repo=CommonCollectionRepository(app);val folder=repo.createFolder(CollectionToolType.TIME_PLAN,"유지",listOf(plan.id))!!
        val backup=success(ArmyristPortableDataManager.validateBackup(success(ArmyristPortableDataManager.createFullBackup(app,null)),null))
        val invalid=JSONObject(backup.collectionSnapshot!!);invalid.getJSONArray("folders").getJSONObject(0).put("toolType","UNKNOWN")
        assertTrue(ArmyristPortableDataManager.restoreFullBackup(app,backup.copy(collectionSnapshot=invalid.toString())) is PortableResult.Error)
        assertEquals(folder,repo.getFolder(folder.id));assertNotNull(app.dateAwareTimePlanRepository.getPlan(plan.id))
    }
    @Test fun restoringLegacyBackupWithoutFoldersDoesNotInventFolders(){
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>()
        val backup=success(ArmyristPortableDataManager.validateBackup(success(ArmyristPortableDataManager.createFullBackup(app,null)),null))
        success(ArmyristPortableDataManager.restoreFullBackup(app,backup.copy(collectionSnapshot=null)))
        assertTrue(CommonCollectionRepository(app).folders(CollectionToolType.TIME_PLAN).isEmpty())
    }
}
