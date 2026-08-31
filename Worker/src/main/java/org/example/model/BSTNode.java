package org.example.model;

import lombok.Data;

@Data
public class BSTNode<K extends Comparable<K>, V> {
    K key;
    V value;
    int height;
    BSTNode<K, V> left;
    BSTNode<K, V> right;

    public BSTNode(K key, V value) {
        this.key = key;
        this.value = value;
        this.height = 1;
        this.left = null;
        this.right = null;
    }
}
