import * as Directories from '../Directories';
import path from 'path';
import glob from 'glob-promise';

/**
 * @param pathToConvert
 * @returns an absolute path to provided location in the expo repo or provided path if it's an absolute path.
 */
export function toRepoPath(pathToConvert: string): string {
  if (path.isAbsolute(pathToConvert)) {
    return pathToConvert;
  }
  return path.join(Directories.getExpoRepositoryRootDir(), pathToConvert);
}

export async function findFiles(directory: string, filePattern: string) {
  return await glob(path.join(directory, filePattern));
}
