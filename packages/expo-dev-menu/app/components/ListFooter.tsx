import React from 'react';
import { StyleSheet, View } from 'react-native';

import { StyledText } from './Text';
import Colors from '../constants/Colors';

export type ListFooterProps = {
  label: string;
};

class ListFooter extends React.PureComponent<ListFooterProps> {
  render() {
    return (
      <View style={styles.container}>
        <StyledText
          style={styles.text}
          lightColor={Colors.light.grayText}
          darkColor={Colors.dark.grayText}>
          {this.props.label}
        </StyledText>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    paddingVertical: 8,
    paddingHorizontal: 20,
  },
  text: {
    fontSize: 12,
  },
});

export default ListFooter;
