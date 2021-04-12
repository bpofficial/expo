import { TypeDocKind } from '~/components/plugins/api/APISectionUtils';

// Generic data type

export type GeneratedData = EnumDefinitionData &
  MethodDefinitionData &
  PropsDefinitionData &
  DefaultPropsDefinitionData &
  TypeGeneralData &
  InterfaceDefinitionData &
  ConstantDefinitionData;

// Shared data types

export type CommentData = {
  text?: string;
  shortText?: string;
  returns?: string;
  tags?: CommentTagData[];
};

export type CommentTagData = {
  tag: string;
  text: string;
};

export type TypeDefinitionData = {
  name: string;
  type: string;
  types?: TypeDefinitionTypesData[];
  elementType?: {
    name: string;
  };
  queryType?: {
    name: string;
  };
  typeArguments?: TypeDefinitionData[];
};

export type TypeDefinitionTypesData = {
  type: string;
  name?: string;
  value?: string | null;
  elementType?: {
    name: string;
  };
};

export type MethodParamData = {
  name: string;
  type: TypeDefinitionData;
  comment?: CommentData;
};

// Constants section

export type ConstantDefinitionData = {
  name: string;
  flags: {
    isConst: boolean;
  };
  comment?: CommentData;
  kind: TypeDocKind;
};

// Enums section

export type EnumDefinitionData = {
  name: string;
  children: EnumValueData[];
  comment?: CommentData;
  kind: TypeDocKind;
};

export type EnumValueData = {
  name: string;
  kind: TypeDocKind;
};

// Interfaces section

export type InterfaceDefinitionData = {
  name: string;
  children: InterfaceValueData[];
  comment?: CommentData;
  kind: TypeDocKind;
};

export type InterfaceValueData = {
  name: string;
  type: TypeDeclarationData;
  kind: TypeDocKind;
  comment?: CommentData;
};

// Methods section

export type MethodDefinitionData = {
  signatures: MethodSignatureData[];
  kind: TypeDocKind;
};

export type MethodSignatureData = {
  name: string;
  parameters: MethodParamData[];
  comment: CommentData;
  type: TypeDefinitionData;
};

// Props section

export type PropsDefinitionData = {
  name: string;
  type: {
    types: TypeDeclarationData[];
  };
  kind: TypeDocKind;
};

export type PropData = {
  name: string;
  comment: CommentData;
  type: TypeDefinitionData;
};

export type DefaultPropsDefinitionData = {
  name: string;
  type: TypeDeclarationData;
  kind: TypeDocKind;
};

// Types section

export type TypeGeneralData = {
  name: string;
  comment: CommentData;
  type: TypeDeclarationData;
  kind: TypeDocKind;
};

export type TypeDeclarationData = {
  declaration?: {
    signatures: TypeSignaturesData[];
    children: TypePropertyData[];
  };
  type: string;
  types: TypeValueData[];
  typeArguments?: TypeDefinitionData[];
};

export type TypeSignaturesData = {
  parameters: MethodParamData[];
};

export type TypePropertyData = {
  name: string;
  flags: {
    isOptional: boolean;
  };
  comment: CommentData;
  type: TypeDefinitionData;
  defaultValue: string;
};

export type TypeValueData = {
  type: string;
  value: string | boolean | null;
};
