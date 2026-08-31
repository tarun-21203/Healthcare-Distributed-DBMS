package org.example.util.TreeUtility;

import java.util.List;

public interface ITreeUtility<K extends Comparable<K>, V> {
    /**
     * Search node ina tree
     *
     * @param key
     * @return
     */
    public V search(K key);

    /**
     * insert a node in a tree
     *
     * @param key
     * @param value
     */
    public void insert(K key, V value);

    /**
     * Update a node in a tree
     *
     * @param key
     * @param value
     * @return
     */
    public boolean update(K key, V value);

    /**
     * delete a node in a tree
     *
     * @param key
     * @return
     */
    public boolean delete(K key);

    /**
     * Get all nodes
     * @return
     */
    public List<V> getAllNodes();
}
