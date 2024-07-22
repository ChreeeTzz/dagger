"""Validates classes in the deployed jar have a max java language level .

Usage:
  python validate-jar-language-level.py <jar-file> <max-java-language-level>
"""

import re
import shutil
import subprocess
import sys
import tempfile
import zipfile


def main(argv):
  if len(argv) > 3:
    raise ValueError(
        'Expected only two arguments but got {0}'.format(len(argv))
    )

  jar_file, java_language_level = argv[-2:]
  if jar_file.endswith('.jar'):
    invalid_entries = _invalid_language_level(jar_file, java_language_level)
  elif jar_file.endswith('.aar'):
    dirpath = tempfile.mkdtemp()
    with zipfile.ZipFile(jar_file, 'r') as zip_file:
      class_file = zip_file.extract('classes.jar', dirpath)
      invalid_entries = _invalid_language_level(class_file, java_language_level)
    shutil.rmtree(dirpath)
  else:
    raise ValueError('Invalid jar file: {0}'.format(jar_file))

  if invalid_entries:
    raise ValueError(
        'Found invalid entries in {0} that do not match the expected java'
        ' language level ({1}):\n    {2}'.format(
            jar_file, java_language_level, '\n    '.join(invalid_entries)
        )
    )


def _invalid_language_level(jar_file, expected_language_level):
  """Returns a list of jar entries with invalid language levels."""
  language_level_pattern = re.compile(r'major version: (\d+)')
  invalid_entries = []
  unique_language_levels = set()
  with zipfile.ZipFile(jar_file, 'r') as zip_file:
    num_files = len(zip_file.infolist())
    for i, info in enumerate(zip_file.infolist()):
      # This can take a while so print an update (on a single line).
      print(
          '\rProcessing {0} ({1} of {2}): Expected {3} - Found {4}'.format(
              jar_file,
              i + 1,
              num_files,
              expected_language_level,
              unique_language_levels,
          ),
          flush=True,
          end='',
      )
      if (
          not info.is_dir()
          and info.filename.endswith('.class')
          and not ignore_entry(info.filename)
      ):
        cmd = 'javap -cp {0} -v {1}'.format(jar_file, info.filename[:-6])
        output1 = subprocess.run(
            cmd.split(),
            stdout=subprocess.PIPE,
            text=True,
            check=True,
        )
        matches = language_level_pattern.findall(output1.stdout)
        if len(matches) != 1:
          raise ValueError('Expected exactly one match but found: %s' % matches)
        class_language_level = matches[0]
        unique_language_levels.add(class_language_level)
        if class_language_level != expected_language_level:
          invalid_entries.append(
              '{0}: {1}'.format(info.filename, class_language_level)
          )
  return invalid_entries


def ignore_entry(filename):
  ignore_entries_prefix = [
      'dagger/spi/internal/shaded/',
      'dagger/grpc/shaded/',
  ]
  for prefix in ignore_entries_prefix:
    if filename.startswith(prefix):
      return True
  return False


if __name__ == '__main__':
  main(sys.argv)
