package org.example.util.TreeUtility;

import org.example.model.BSTNode;

import java.util.ArrayList;
import java.util.List;

public class BST<K extends Comparable<K>, V> implements ITreeUtility<K, V> {

    private BSTNode<K, V> root;
    private int size;

    public BST() {
        root = null;
        size = 0;
    }

    private int height(BSTNode<K, V> node) {
        return node == null ? 0 : node.getHeight();
    }

    private void updateHeight(BSTNode<K, V> node) {
        if (node != null) {
            node.setHeight(1 + Math.max(height(node.getLeft()), height(node.getRight())));
        }
    }

    private int getBalanceComparison(BSTNode<K, V> node) {
        return node == null ? 0 : height(node.getLeft()) - height(node.getRight());
    }


    private BSTNode<K, V> balanceTree(BSTNode<K, V> node) {
        updateHeight(node);
        int comparison = getBalanceComparison(node);

        // left left tree
        if (comparison > 1 && getBalanceComparison(node.getLeft()) >= 0) {
            return rotateRight(node);
        }

        // left right tree
        else if (comparison > 1 && getBalanceComparison(node.getLeft()) < 0) {
            node.setLeft(rotateLeft(node.getLeft()));
            return rotateRight(node);
        }

        //right right tree
        else if (comparison < -1 && getBalanceComparison(node.getRight()) <= 0) {
            return rotateLeft(node);
        }

        //right left tree
        else if (comparison < -1 && getBalanceComparison(node.getRight()) > 0) {
            node.setRight(rotateRight(node.getRight()));
            return rotateLeft(node);
        }
        return node;
    }

    private BSTNode<K, V> rotateLeft(BSTNode<K, V> node) {
        BSTNode<K, V> node2 = node.getRight();
        BSTNode<K, V> dangNode = node2.getLeft();

        node2.setLeft(node);
        node.setRight(dangNode);

        updateHeight(node);
        updateHeight(node2);

        return node2;
    }

    private BSTNode<K, V> rotateRight(BSTNode<K, V> node) {

        BSTNode<K, V> node2 = node.getLeft();
        BSTNode<K, V> dangNode = node2.getRight();

        node2.setRight(node);
        node.setLeft(dangNode);

        updateHeight(node);
        updateHeight(node2);

        return node2;
    }

    private BSTNode<K, V> insertNode(BSTNode<K, V> node, K key, V value) {
        if (node == null) {
            size++;
            return new BSTNode<>(key, value);
        }
        int comparison = key.compareTo(node.getKey());
        if (comparison < 0) {
            node.setLeft(insertNode(node.getLeft(), key, value));
        } else if (comparison > 0) {
            node.setRight(insertNode(node.getRight(), key, value));
        } else {
            node.setValue(value);
        }
        return balanceTree(node);
    }

    private V searchNode(BSTNode<K, V> node, K key) {
        if (node == null) {
            return null;
        }
        int comparison = key.compareTo(node.getKey());
        if (comparison == 0) {
            return node.getValue();
        } else if (comparison < 0) {
            return searchNode(node.getLeft(), key);
        } else {
            return searchNode(node.getRight(), key);
        }
    }

    private BSTNode<K, V> deleteNode(BSTNode<K, V> node, K key) {
        if (node == null) {
            return null;
        }
        int comparison = key.compareTo(node.getKey());
        if (comparison < 0) {
            node.setLeft(deleteNode(node.getLeft(), key));
        } else if (comparison > 0) {
            node.setRight(deleteNode(node.getRight(), key));
        } else {
            if (node.getLeft() == null) {
                return node.getRight();
            } else if (node.getRight() == null) {
                return node.getLeft();
            } else {
                BSTNode<K, V> successor = findSuccessor(node.getRight());
                node.setKey(successor.getKey());
                node.setValue(successor.getValue());
                node.setRight(deleteNode(node.getRight(), successor.getKey()));
            }
        }
        return balanceTree(node);
    }

    private BSTNode<K, V> findSuccessor(BSTNode<K, V> node) {
        if (node.getLeft() == null) return node;
        else return findSuccessor(node.getLeft());
    }

    @Override
    public V search(K key) {
        return searchNode(root, key);
    }

    @Override
    public void insert(K key, V value) {
        root = insertNode(root, key, value);
    }

    @Override
    public boolean update(K key, V value) {
        if (search(key) != null) {
            root = insertNode(root, key, value);
            return true;
        }
        return false;
    }

    @Override
    public boolean delete(K key) {
        if (search(key) == null) {
            System.out.println("failed");
            return false;
        }
        root = deleteNode(root, key);
        size--;
        return true;
    }

    @Override
    public List<V> getAllNodes() {
        List<V> nodes = new ArrayList<>();
        inOrderValue(root, nodes);
        return nodes;
    }

    private void inOrderValue(BSTNode<K, V> node, List<V> list) {
        if (node == null) return;
        inOrderValue(node.getLeft(), list);
        list.add(node.getValue());
        inOrderValue(node.getRight(), list);
    }
}
