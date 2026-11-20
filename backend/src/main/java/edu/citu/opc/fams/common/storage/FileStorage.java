package edu.citu.opc.fams.common.storage;

import java.io.IOException;

/**
 * Where uploaded files (asset photos, scanned documents, generated PDFs) are kept. The local-disk
 * implementation is used in development; a Supabase Storage implementation replaces it at deploy
 * (Step 9) without any module changing.
 */
public interface FileStorage {

    /** Saves the bytes and returns the storage path to keep in the database. */
    String save(String path, byte[] content, String contentType) throws IOException;

    /** Reads a file saved earlier. */
    byte[] load(String path) throws IOException;

    void delete(String path) throws IOException;
}
