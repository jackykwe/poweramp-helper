package com.kaeonx.poweramphelper.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

internal class MusicFileRepository(private val musicFileDAO: MusicFileDAO) {
    internal fun getMusicFilesInMusicFolderLangMinusFlow(parentDirEncodedUri: String): Flow<List<MusicFile>> {
        return musicFileDAO.getMusicFilesInMusicFolderLangMinusFlow(parentDirEncodedUri).distinctUntilChanged()
    }

    internal fun getMusicFilesInMusicFolderRating0SFlow(parentDirEncodedUri: String): Flow<List<MusicFile>> {
        return musicFileDAO.getMusicFilesInMusicFolderRating0SFlow(parentDirEncodedUri).distinctUntilChanged()
    }

    internal suspend fun setLangCh(parentDirEncodedUri: String, fileName: String) {
        musicFileDAO.setLangCh(parentDirEncodedUri, fileName)
    }

    internal suspend fun setLangCN(parentDirEncodedUri: String, fileName: String) {
        musicFileDAO.setLangCN(parentDirEncodedUri, fileName)
    }

    internal suspend fun setLangEN(parentDirEncodedUri: String, fileName: String) {
        musicFileDAO.setLangEN(parentDirEncodedUri, fileName)
    }

    internal suspend fun setLangJP(parentDirEncodedUri: String, fileName: String) {
        musicFileDAO.setLangJP(parentDirEncodedUri, fileName)
    }

    internal suspend fun setLangKR(parentDirEncodedUri: String, fileName: String) {
        musicFileDAO.setLangKR(parentDirEncodedUri, fileName)
    }

    internal suspend fun setLangO(parentDirEncodedUri: String, fileName: String) {
        musicFileDAO.setLangO(parentDirEncodedUri, fileName)
    }

    internal suspend fun ensurePresentFiles(musicFiles: List<MusicFile>) {
        musicFileDAO.ensurePresentFiles(musicFiles)
    }

    internal suspend fun reset() {
        musicFileDAO.reset()
    }
}