# Writeup for Lab 2

# 517030910422 Zihan Yu

## Design decisions
1. ***Eviction***: LRU policy is applied. Using LRU hashmap to keep track of the usage of each page, i.e., when a passage is visited, LRU hashmap will be updated by resetting the visited page and let other pages' LRU values decrease by 1. The page with smallest LRU value will be evicted.
2. ***Insertion***: Buffer Pool will call the insertion method in BTreeFile. It will find the leaf page and then split leaf page and split internal pages recursively if full. Then add the tuple to the leaf. Those dirty pages will be returned. Buffer Pool will mark them and add those dirty pages into cache.
3. ***deletion***: Find the leaf, delete the tuple and then steal/merge if needed. Buffer Pool will do similar things as in Insertion.

## API changes
No API changes.

## Missing or incomplete element
No missing or incomplete element

## time spent and difficult/confusing parts
It takes me about 3 days to finish lab 2. The most difficult part may be debug. 