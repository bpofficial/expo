import { css } from '@emotion/core';
import { theme } from '@expo/styleguide';
import React from 'react';
import ReactMarkdown from 'react-markdown';

import { Code, InlineCode } from '~/components/base/code';
import { H4 } from '~/components/base/headings';
import { InternalLink } from '~/components/base/link';
import { LI, UL } from '~/components/base/list';
import { B, P, Quote } from '~/components/base/paragraph';
import {
  CommentData,
  MethodParamData,
  TypeDefinitionData,
  TypeDefinitionTypesData,
} from '~/components/plugins/api/APIDataTypes';

export enum TypeDocKind {
  Enum = 4,
  Variable = 32,
  Function = 64,
  Class = 128,
  Interface = 256,
  Property = 1024,
  TypeAlias = 4194304,
}

export type MDRenderers = React.ComponentProps<typeof ReactMarkdown>['renderers'];

export const mdRenderers: MDRenderers = {
  blockquote: ({ children }) => (
    <Quote>
      {React.Children.map(children, child =>
        child.type.name === 'paragraph' ? child.props.children : child
      )}
    </Quote>
  ),
  code: ({ value, language }) => <Code className={`language-${language}`}>{value}</Code>,
  heading: ({ children }) => <H4>{children}</H4>,
  inlineCode: ({ value }) => <InlineCode>{value}</InlineCode>,
  list: ({ children }) => <UL>{children}</UL>,
  listItem: ({ children }) => <LI>{children}</LI>,
  link: ({ href, children }) => <InternalLink href={href}>{children}</InternalLink>,
  paragraph: ({ children }) => (children ? <P>{children}</P> : null),
  strong: ({ children }) => <B>{children}</B>,
  text: ({ value }) => (value ? <span>{value}</span> : null),
};

export const mdInlineRenderers: MDRenderers = {
  ...mdRenderers,
  paragraph: ({ children }) => (children ? <span>{children}</span> : null),
};

const nonLinkableTypes = ['Date', 'Uint8Array'];

export const resolveTypeName = ({
  elementType,
  name,
  type,
  types,
  typeArguments,
  declaration,
}: TypeDefinitionData): string | JSX.Element => {
  if (name) {
    if (type === 'reference') {
      if (typeArguments) {
        if (name === 'Promise') {
          return (
            <span>
              {'Promise<'}
              {typeArguments.map(resolveTypeName)}
              {'>'}
            </span>
          );
        } else {
          return `${typeArguments.map(resolveTypeName)}`;
        }
      } else {
        if (nonLinkableTypes.includes(name)) {
          return name;
        } else {
          return (
            <InternalLink href={`#${name.toLowerCase()}`} key={`type-link-${name}`}>
              {name}
            </InternalLink>
          );
        }
      }
    } else {
      return name;
    }
  } else if (elementType?.name) {
    if (type === 'array') {
      return elementType.name + '[]';
    }
    return elementType.name + type;
  } else if (type === 'union' && types?.length) {
    return types
      .map((t: TypeDefinitionTypesData) =>
        t.type === 'array' ? `${t.elementType?.name}[]` : `${t.name || t.value}`
      )
      .join(' | ');
  } else if (declaration?.signatures) {
    const baseSignature = declaration.signatures[0];
    if (baseSignature?.parameters?.length) {
      return (
        <>
          (
          {baseSignature.parameters?.map((param, index) => (
            <span key={`param-${index}-${param.name}`}>
              {param.name}: {resolveTypeName(param.type)}
              {index + 1 !== baseSignature.parameters.length && ', '}
            </span>
          ))}
          ) {'=>'} {resolveTypeName(baseSignature.type)}
        </>
      );
    } else {
      return `() => ${resolveTypeName(baseSignature.type)}`;
    }
  }
  return 'undefined';
};

export const renderParam = ({ comment, name, type }: MethodParamData): JSX.Element => (
  <LI key={`param-${name}`}>
    <B>
      {name} (<InlineCode>{resolveTypeName(type)}</InlineCode>)
    </B>
    <CommentTextBlock comment={comment} renderers={mdInlineRenderers} withDash />
  </LI>
);

export type CommentTextBlockProps = {
  comment?: CommentData;
  renderers?: MDRenderers;
  withDash?: boolean;
};

export const CommentTextBlock: React.FC<CommentTextBlockProps> = ({
  comment,
  renderers = mdRenderers,
  withDash,
}) => {
  const shortText = comment?.shortText?.trim().length ? (
    <ReactMarkdown renderers={renderers}>{comment.shortText}</ReactMarkdown>
  ) : null;
  const text = comment?.text?.trim().length ? (
    <ReactMarkdown renderers={renderers}>{comment.text}</ReactMarkdown>
  ) : null;
  return (
    <>
      {withDash && (shortText || text) ? ' - ' : null}
      {shortText}
      {text}
    </>
  );
};

export const STYLES_OPTIONAL = css`
  color: ${theme.text.secondary};
  font-size: 90%;
  padding-top: 22px;
`;
