<?php

use Drupal\node\Entity\Node;
use Drupal\media\Entity\Media;
use Drupal\taxonomy\Entity\Term;

$action_manager = \Drupal::entityTypeManager()->getStorage('action');

$tids = \Drupal::entityQuery('taxonomy_term')->execute();
$terms = Term::loadMultiple($tids);
$action_manager->load('index_taxonomy_term_in_fedora')->execute($terms);
$action_manager->load('index_taxonomy_term_in_the_triplestore')->execute($terms);

$nids = \Drupal::entityQuery('node')->execute();
$nodes = Node::loadMultiple($nids);
$action_manager->load('index_node_in_fedora')->execute($nodes);
$action_manager->load('index_node_in_triplestore')->execute($nodes);

$mids = \Drupal::entityQuery('media')->execute();
$media = Media::loadMultiple($mids);
$action_manager->load('index_media_in_fedora')->execute($media);
$action_manager->load('index_media_in_triplestore')->execute($media);
