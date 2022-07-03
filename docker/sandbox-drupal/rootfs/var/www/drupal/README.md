# Drupal Site <!-- omit in toc -->

- [Assets](#assets)

# Assets

There are two types of files here, both of which are applied as part of the composer.json project.

`default.settings.php` is appended to the end of
`web/sites/default/default.settings.php` via composer.

It is used to apply settings from environment variables in the container.

All other are patches to fix bugs or change some behavior in core / contrib modules.
