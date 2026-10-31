/**
 * ATOM W11: COMPACT SINGLE-CHILD DIRECTORY RENDERING
 * Path: apps/web/src/components/workbench/CompactDirectoryMerger.ts
 *
 * Collapses sequential single-child directory chains into a unified compact breadcrumb row
 * (e.g. "src/main/kotlin") within the explorer tree. Expands back when editing or toggled.
 */

export interface DirectoryNode {
  name: string;
  path: string;
  isDirectory: boolean;
  children?: DirectoryNode[];
  isExpanded?: boolean;
}

export interface CompactFolderNode {
  displayName: string;
  fullPath: string;
  originalSegments: string[];
  isDirectory: boolean;
  children?: CompactFolderNode[];
  isExpanded?: boolean;
  isCompactChain: boolean;
}

export interface CompactDirectoryOptions {
  compactFolders: boolean;
  separator?: string;
}

export class CompactDirectoryMerger {
  private compactFolders: boolean;
  private separator: string;

  constructor(options: CompactDirectoryOptions = { compactFolders: true }) {
    this.compactFolders = options.compactFolders;
    this.separator = options.separator ?? '/';
  }

  public setCompactFolders(enabled: boolean): void {
    this.compactFolders = enabled;
  }

  public isCompactFoldersEnabled(): boolean {
    return this.compactFolders;
  }

  /**
   * Compresses sequential single-child directory nodes into combined nodes.
   */
  public compactTree(nodes: DirectoryNode[]): CompactFolderNode[] {
    return nodes.map((node) => this.compactSingleNode(node));
  }

  private compactSingleNode(node: DirectoryNode): CompactFolderNode {
    if (!node.isDirectory || !node.children) {
      return {
        displayName: node.name,
        fullPath: node.path,
        originalSegments: [node.name],
        isDirectory: node.isDirectory,
        children: node.children ? this.compactTree(node.children) : undefined,
        isExpanded: node.isExpanded,
        isCompactChain: false,
      };
    }

    if (!this.compactFolders) {
      return {
        displayName: node.name,
        fullPath: node.path,
        originalSegments: [node.name],
        isDirectory: true,
        children: this.compactTree(node.children),
        isExpanded: node.isExpanded,
        isCompactChain: false,
      };
    }

    // Check if node is single-child directory
    let current: DirectoryNode = node;
    const segments: string[] = [node.name];
    let isChain = false;

    while (
      current.isDirectory &&
      current.children &&
      current.children.length === 1 &&
      current.children[0].isDirectory
    ) {
      isChain = true;
      current = current.children[0];
      segments.push(current.name);
    }

    const processedChildren = current.children
      ? this.compactTree(current.children)
      : [];

    return {
      displayName: segments.join(this.separator),
      fullPath: current.path,
      originalSegments: segments,
      isDirectory: true,
      children: processedChildren,
      isExpanded: current.isExpanded ?? node.isExpanded,
      isCompactChain: isChain,
    };
  }

  /**
   * Unrolls a compact folder node back into normal nested representation.
   */
  public expandCompactChain(compactNode: CompactFolderNode): DirectoryNode {
    if (!compactNode.isCompactChain || compactNode.originalSegments.length <= 1) {
      return {
        name: compactNode.displayName,
        path: compactNode.fullPath,
        isDirectory: compactNode.isDirectory,
        children: compactNode.children?.map((c) => this.unrollNode(c)),
        isExpanded: compactNode.isExpanded,
      };
    }

    const segments = [...compactNode.originalSegments];
    const rootName = segments.shift()!;
    let currentPath = rootName;

    const root: DirectoryNode = {
      name: rootName,
      path: currentPath,
      isDirectory: true,
      children: [],
      isExpanded: true,
    };

    let parent = root;
    while (segments.length > 0) {
      const seg = segments.shift()!;
      currentPath = `${currentPath}/${seg}`;
      const isLeafSegment = segments.length === 0;

      const child: DirectoryNode = {
        name: seg,
        path: isLeafSegment ? compactNode.fullPath : currentPath,
        isDirectory: true,
        children: isLeafSegment
          ? compactNode.children?.map((c) => this.unrollNode(c)) ?? []
          : [],
        isExpanded: isLeafSegment ? compactNode.isExpanded : true,
      };

      parent.children = [child];
      parent = child;
    }

    return root;
  }

  private unrollNode(node: CompactFolderNode): DirectoryNode {
    if (node.isCompactChain) {
      return this.expandCompactChain(node);
    }
    return {
      name: node.displayName,
      path: node.fullPath,
      isDirectory: node.isDirectory,
      children: node.children?.map((c) => this.unrollNode(c)),
      isExpanded: node.isExpanded,
    };
  }
}

export const defaultCompactDirectoryMerger = new CompactDirectoryMerger();
